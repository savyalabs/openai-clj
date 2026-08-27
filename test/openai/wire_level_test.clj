(ns openai.wire-level-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [openai.core :as openai]
            [openai.realtime :as realtime])
  (:import (com.sun.net.httpserver HttpExchange HttpHandler HttpServer)
           (java.io BufferedInputStream ByteArrayOutputStream DataInputStream)
           (java.net InetAddress InetSocketAddress ServerSocket Socket)
           (java.nio.charset StandardCharsets)
           (java.security MessageDigest)
           (java.util Base64)
           (java.util.concurrent Executors)
           (okhttp3 OkHttpClient)))

(defn- read-bytes [^HttpExchange exchange]
  (with-open [in (.getRequestBody exchange)]
    (let [out (ByteArrayOutputStream.)
          buf (byte-array 1024)]
      (loop []
        (let [n (.read in buf)]
          (when (pos? n)
            (.write out buf 0 n)
            (recur))))
      (.toString out "UTF-8"))))

(defn- start-http-fixture! [handler]
  (let [server (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)
        executor (Executors/newCachedThreadPool)]
    (.createContext server "/" (reify HttpHandler
                                  (handle [_ exchange]
                                    (handler exchange))))
    (.setExecutor server executor)
    (.start server)
    {:server server
     :executor executor
     :base-url (str "http://127.0.0.1:" (.getPort (.getAddress server)) "/v1")}))

(defn- stop-http-fixture! [{:keys [^HttpServer server ^java.util.concurrent.ExecutorService executor]}]
  (.stop server 0)
  (.shutdownNow executor))

(defn- respond! [^HttpExchange exchange status content-type body]
  (let [bytes (.getBytes body StandardCharsets/UTF_8)]
    (.set (.getResponseHeaders exchange) "Content-Type" content-type)
    (.sendResponseHeaders exchange status (alength bytes))
    (with-open [out (.getResponseBody exchange)]
      (.write out bytes))))

(defn- websocket-accept [key]
  (.encodeToString
   (Base64/getEncoder)
   (.digest (MessageDigest/getInstance "SHA-1")
            (.getBytes (str key "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                       StandardCharsets/US_ASCII))))

(defn- read-until [^BufferedInputStream in delimiter]
  (let [out (ByteArrayOutputStream.)]
    (loop [matched 0]
      (let [b (.read in)]
        (when (neg? b) (throw (ex-info "fixture socket closed" {})))
        (.write out b)
        (let [next-matched (if (= b (int (nth delimiter matched)))
                             (inc matched)
                             0)]
          (if (= next-matched (count delimiter))
            (.toString out "UTF-8")
            (recur next-matched)))))))

(defn- read-frame! [^BufferedInputStream in]
  (let [first-byte (.read in)
        second-byte (.read in)
        masked (pos? (bit-and second-byte 0x80))
        length (bit-and second-byte 0x7f)
        length (cond
                 (= length 126) (bit-or (bit-shift-left (.read in) 8) (.read in))
                 (= length 127) (throw (ex-info "fixture only supports short frames" {}))
                 :else length)
        mask (when masked (byte-array (repeatedly 4 #(.read in))))
        payload (byte-array length)]
    (.readFully (DataInputStream. in) payload)
    (when masked
      (dotimes [i length]
        (aset-byte payload i (unchecked-byte
                              (bit-xor (bit-and (aget payload i) 0xff)
                                       (bit-and (aget mask (mod i 4)) 0xff))))))
    {:opcode (bit-and first-byte 0x0f)
     :payload (.toString (doto (ByteArrayOutputStream.) (.write payload)) "UTF-8")}))

(defn- write-frame! [out opcode payload]
  (let [bytes (if (string? payload)
                (.getBytes payload StandardCharsets/UTF_8)
                payload)]
    (.write out (bit-or 0x80 opcode))
    (.write out (alength bytes))
    (.write out bytes)
    (.flush out)))

(defn- start-websocket-fixture! []
  (let [socket-server (ServerSocket. 0 50 (InetAddress/getByName "127.0.0.1"))
        received (promise)
        closed (promise)
        continue-to-close (promise)
        server-error (promise)
        server (future
                 (try
                   (with-open [^Socket socket (.accept socket-server)]
                     (let [in (BufferedInputStream. (.getInputStream socket))
                           out (.getOutputStream socket)
                           headers (read-until in "\r\n\r\n")
                           key (some-> (re-find #"(?i)Sec-WebSocket-Key:\s*([^\r\n]+)" headers)
                                       second
                                       str/trim)]
                       (.write out (.getBytes (str "HTTP/1.1 101 Switching Protocols\r\n"
                                                    "Upgrade: websocket\r\n"
                                                    "Connection: Upgrade\r\n"
                                                    "Sec-WebSocket-Accept: " (websocket-accept key) "\r\n\r\n")
                                                StandardCharsets/US_ASCII))
                       (.flush out)
                       (deliver received (read-frame! in))
                       (write-frame! out 1 "{\"type\":\"fixture.ready\",\"message\":\"hello\"}")
                       (deref continue-to-close 5000 nil)
                       (deliver closed (read-frame! in))
                       (write-frame! out 8 "")))
                   (catch Throwable e (do (deliver server-error e)
                                          (deliver received {:server-error (str e)})
                                          (deliver closed {:server-error (str e)}))))) ]
    {:url (str "ws://127.0.0.1:" (.getLocalPort socket-server) "/v1/realtime")
     :server socket-server :received received :closed closed :continue-to-close continue-to-close
     :server-error server-error :task server}))

(defn- stop-websocket-fixture! [{:keys [^ServerSocket server task]}]
  (.close server)
  (try (deref task 1000 nil) (catch java.util.concurrent.ExecutionException _ nil)))

(deftest chat-create-sends-exact-wire-request-to-local-fixture
  (let [request (promise)
        fixture (start-http-fixture!
                 (fn [^HttpExchange exchange]
                   (deliver request {:method (.getRequestMethod exchange)
                                     :path (.getPath (.getRequestURI exchange))
                                     :authorization (.getFirst (.getRequestHeaders exchange) "Authorization")
                                     :content-type (.getFirst (.getRequestHeaders exchange) "Content-Type")
                                     :body (read-bytes exchange)})
                   (respond! exchange 200 "application/json"
                             "{\"id\":\"chat_1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"gpt-test\",\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}")))
        client (openai/client {:api-key "test-key" :base-url (:base-url fixture)})]
    (try
      (openai/create-chat-completion client
                                     {:model "gpt-test" :messages [{:role :user :content "hello"}]})
      (is (= {:method "POST" :path "/v1/chat/completions"
              :authorization "Bearer test-key" :content-type "application/json"
              :body "{\"messages\":[{\"content\":\"hello\",\"role\":\"user\"}],\"model\":\"gpt-test\"}"}
             (deref request 1000 ::timeout)))
      (finally (.close client) (stop-http-fixture! fixture)))))

(deftest chat-streaming-uses-local-sse-fixture
  (let [fixture (start-http-fixture!
                 (fn [^HttpExchange exchange]
                   (read-bytes exchange)
                   (respond! exchange 200 "text/event-stream"
                             (str "data: {\"id\":\"chat_1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hel\"},\"finish_reason\":null}]}\n\n"
                                  "data: {\"id\":\"chat_1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}]}\n\n"
                                  "data: [DONE]\n\n"))))
        client (openai/client {:api-key "test-key" :base-url (:base-url fixture)})]
    (try
      (is (= "Hello"
             (openai/stream-chat-completion-text
              client
              {:model "gpt-test" :messages [{:role :user :content "hello"}]}
              nil)))
      (finally (.close client) (stop-http-fixture! fixture)))))

(deftest api-errors-preserve-local-error-response-shape
  (let [fixture (start-http-fixture!
                 (fn [^HttpExchange exchange]
                   (read-bytes exchange)
                   (respond! exchange 401 "application/json"
                             "{\"error\":{\"message\":\"bad key\",\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}")))
        client (openai/client {:api-key "test-key" :base-url (:base-url fixture)})]
    (try
      (let [result (try
                     (openai/create-chat-completion
                      client
                      {:model "gpt-test" :messages [{:role :user :content "hello"}]})
                     nil
                     (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= :api-error (:openai/error result)))
        (is (= 401 (:status result)))
        (is (= {:openai/error :api-error :status 401 :error-type :unauthorized}
               result)))
      (finally (.close client) (stop-http-fixture! fixture)))))

(deftest realtime-round-trip-uses-local-websocket-fixture
  (let [fixture (start-websocket-fixture!)
        http (OkHttpClient.)]
    (try
      (let [opened (promise)
            connection (realtime/connect {:api-key "test-key" :url (:url fixture)
                                          :okhttp-client http
                                          :on-open (fn [_ _] (deliver opened true))})]
        (try
          (is (= true (deref opened 5000 ::timeout)))
          (realtime/send! connection {:type :session.update :event-id "evt_1"})
          (is (= {:opcode 1 :payload "{\"type\":\"session.update\",\"event_id\":\"evt_1\"}"}
                 (deref (:received fixture) 5000 ::timeout)))
          (is (= {:type :fixture.ready :message "hello"}
                 (realtime/poll! connection 5000)))
          (finally
            (deliver (:continue-to-close fixture) true)
            (.close connection))))
      (let [closed-frame (deref (:closed fixture) 5000 ::timeout)]
        (is (= 8 (:opcode closed-frame)) (pr-str closed-frame)))
      (finally
        (-> http .dispatcher .executorService .shutdown)
        (-> http .connectionPool .evictAll)
        (stop-websocket-fixture! fixture)))))
