(ns openai.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.core :as openai]
            [openai.impl :as impl])
  (:import (com.openai.auth SubjectTokenProvider
                            SubjectTokenType
                            WorkloadIdentity)
           (com.openai.client OpenAIClient)
           (com.openai.core JsonValue LogLevel)
           (com.openai.models.chat.completions ChatCompletion
                                               ChatCompletion$Choice
                                               ChatCompletion$Choice$FinishReason
                                               ChatCompletion$ServiceTier
                                               ChatCompletionChunk
                                               ChatCompletionChunk$Choice
                                               ChatCompletionChunk$Choice$Delta
                                               ChatCompletionChunk$Choice$Delta$Role
                                               ChatCompletionChunk$Choice$Delta$ToolCall
                                               ChatCompletionChunk$Choice$Delta$ToolCall$Function
                                               ChatCompletionChunk$Choice$Delta$ToolCall$Type
                                               ChatCompletionChunk$Choice$FinishReason
                                               ChatCompletionContentPart
                                               ChatCompletionCreateParams
                                               ChatCompletionMessage
                                               ChatCompletionMessageFunctionToolCall
                                               ChatCompletionMessageFunctionToolCall$Function
                                               ChatCompletionMessageToolCall
                                               ChatCompletionMessageParam
                                               ChatCompletionStoreMessage)
           (com.openai.models.completions CompletionUsage)
           (com.openai.models.models Model)
           (com.openai.models.responses ResponseCreateParams
                                        ResponseCreateParams$ToolChoice
                                        ResponseIncludable
                                        Response$IncompleteDetails
                                        Response$IncompleteDetails$Reason
                                        ResponseCompletedEvent
                                        ResponseCreatedEvent
                                        ResponseErrorEvent
                                        ResponseFailedEvent
                                        ResponseFunctionToolCall
                                        ResponseFunctionToolCallOutputItem
                                        ResponseFunctionToolCallOutputItem$Output
                                        ResponseFunctionToolCallOutputItem$Output$FunctionAndCustomToolCallOutput
                                        ResponseFunctionToolCallOutputItem$Status
                                        ResponseInputText
                                        ResponseFunctionCallArgumentsDeltaEvent
                                        ResponseFunctionCallArgumentsDoneEvent
                                        ResponseIncompleteEvent
                                        ResponseInProgressEvent
                                        ResponseInputContent
                                        ResponseInputItem
                                        ResponseItem
                                        McpToolCallError
                                        McpToolCallError$McpProtocolError
                                        ResponseOutputItemAddedEvent
                                        ResponseOutputItemDoneEvent
                                        ResponseOutputItem
                                        ResponseOutputItem$McpCall
                                        ResponseOutputItem$McpCall$Status
                                        ResponseOutputItem$McpListTools
                                        ResponseOutputItem$McpListTools$Tool
                                        ResponseOutputItem$ImageGenerationCall
                                        ResponseOutputItem$ImageGenerationCall$Status
                                        ResponseOutputItem$LocalShellCall
                                        ResponseOutputItem$LocalShellCall$Action
                                        ResponseOutputItem$LocalShellCall$Action$Env
                                        ResponseOutputItem$LocalShellCall$Status
                                        ResponseOutputMessage
                                        ResponseOutputMessage$Content
                                        ResponseOutputMessage$Status
                                        ResponseOutputRefusal
                                        ResponseOutputText
                                        ResponseOutputText$Annotation
                                        ResponseOutputText$Annotation$UrlCitation
                                        ResponseOutputText$Logprob
                                        ResponseOutputText$Logprob$TopLogprob
                                        ResponseReasoningItem
                                        ResponseReasoningItem$Status
                                        ResponseReasoningItem$Summary
                                        ResponseCustomToolCall
                                        ResponseComputerToolCall
                                        ResponseComputerToolCall$Status
                                        ResponseComputerToolCall$Type
                                        ResponseReasoningTextDeltaEvent
                                        ResponseReasoningTextDoneEvent
                                        ResponseRefusalDeltaEvent
                                        ResponseRefusalDoneEvent
                                        ResponseStatus
                                        ResponseStreamEvent
                                        ResponseTextDeltaEvent
                                        ResponseTextDoneEvent
                                        ResponseUsage
                                        ResponseUsage$InputTokensDetails
                                        ResponseUsage$OutputTokensDetails
                                        ToolChoiceOptions)
           (com.openai.models.responses.inputitems InputItemListParams
                                                   InputItemListParams$Order)
           (java.net InetSocketAddress Proxy Proxy$Type)
           (java.util.concurrent ExecutorService Executors)))

(defn- params ^ResponseCreateParams [m]
  (#'openai/->params m))

(defn- response->map [r]
  (#'openai/response->map r))

(defn- response->map-with-options [r opts]
  (#'openai/response->map r opts))

(defn- response-item->map [item]
  (#'openai/response-item->map item))

(defn- output-item->map [item]
  (#'openai/output-item->map item))

(defn- event->map [e]
  (#'openai/event->map e))

(defn- model->map [m]
  (#'openai/model->map m))

(defn- input-token-count-params [m]
  (#'openai/->input-token-count-params m))

(deftest accepts-all-stable-response-input-item-variants
  (doseq [[type item? item]
          [[:additional-tools #(.isAdditionalTools %) {:type :additional-tools :tools []}]
           [:apply-patch-call #(.isApplyPatchCall %) {:type :apply-patch-call :call-id "c" :operation {:type :update-file :path "p" :diff "d"} :status :completed}]
           [:apply-patch-call-output #(.isApplyPatchCallOutput %) {:type :apply-patch-call-output :call-id "c" :status :completed :output "ok"}]
           [:code-interpreter-call #(.isCodeInterpreterCall %) {:type :code-interpreter-call :id "c" :code "x" :container-id "ct" :status :completed :outputs []}]
           [:compaction #(.isCompaction %) {:type :compaction :id "c"}]
           [:compaction-trigger #(.isCompactionTrigger %) {:type :compaction-trigger}]
           [:computer-call #(.isComputerCall %) {:type :computer-call :id "c" :call-id "cc" :pending-safety-checks [] :status :completed}]
           [:computer-call-output #(.isComputerCallOutput %) {:type :computer-call-output :call-id "cc" :output {:image-url "data:image/png;base64,x"}}]
           [:custom-tool-call #(.isCustomToolCall %) {:type :custom-tool-call :id "c" :call-id "cc" :name "n" :input "x"}]
           [:custom-tool-call-output #(.isCustomToolCallOutput %) {:type :custom-tool-call-output :call-id "cc" :output "x"}]
           [:easy-input-message #(.isEasyInputMessage %) {:type :easy-input-message :role :user :content "x"}]
           [:file-search-call #(.isFileSearchCall %) {:type :file-search-call :id "f" :queries ["q"] :status :completed :results []}]
           [:function-call #(.isFunctionCall %) {:type :function-call :id "f" :call-id "c" :name "n" :arguments "{}"}]
           [:function-call-output #(.isFunctionCallOutput %) {:type :function-call-output :call-id "c" :output "x"}]
           [:image-generation-call #(.isImageGenerationCall %) {:type :image-generation-call :id "i" :status :completed :result "x"}]
           [:item-reference #(.isItemReference %) {:type :item-reference :id "i"}]
           [:local-shell-call #(.isLocalShellCall %) {:type :local-shell-call :id "l" :call-id "c" :action {:type :exec :command ["pwd"] :env {}} :status :completed}]
           [:local-shell-call-output #(.isLocalShellCallOutput %) {:type :local-shell-call-output :id "l" :output "x" :status :completed}]
           [:mcp-approval-request #(.isMcpApprovalRequest %) {:type :mcp-approval-request :id "a" :arguments "{}" :name "n" :server-label "s"}]
           [:mcp-approval-response #(.isMcpApprovalResponse %) {:type :mcp-approval-response :approval-request-id "a" :approve true}]
           [:mcp-call #(.isMcpCall %) {:type :mcp-call :id "m" :arguments "{}" :name "n" :server-label "s" :status :completed}]
           [:mcp-list-tools #(.isMcpListTools %) {:type :mcp-list-tools :id "m" :server-label "s" :tools []}]
           [:message #(.isMessage %) {:type :message :role :user :content [{:type :text :text "x"}]}]
           [:program #(.isProgram %) {:type :program :id "p" :call-id "c" :code "x" :fingerprint "f"}]
           [:program-output #(.isProgramOutput %) {:type :program-output :id "p" :call-id "c" :result "x" :status :completed}]
           [:reasoning #(.isReasoning %) {:type :reasoning :id "r" :summary []}]
           [:response-output-message #(.isResponseOutputMessage %) {:type :response-output-message :id "m" :role :assistant :status :completed :content [{:type :output-text :text "x" :annotations []}]}]
           [:shell-call #(.isShellCall %) {:type :shell-call :id "s" :call-id "c" :action :exec :environment :local :status :completed}]
           [:shell-call-output #(.isShellCallOutput %) {:type :shell-call-output :id "s" :call-id "c" :output [{:stdout "x" :stderr "" :exit-code 0}] :status :completed}]
           [:tool-search-call #(.isToolSearchCall %) {:type :tool-search-call :id "t" :call-id "c" :arguments {} :execution :client :status :completed}]
           [:tool-search-output #(.isToolSearchOutput %) {:type :tool-search-output :id "t" :call-id "c" :tools [] :execution :client :status :completed}]
           [:web-search-call #(.isWebSearchCall %) {:type :web-search-call :id "w" :action {:type :search :query "q" :queries ["q"] :sources []} :status :completed}]]]
    (is (item? (openai/response-input-item item)) (str type))))

(defn- chat-params ^ChatCompletionCreateParams [m]
  (#'openai/->chat-params m))

(defn- chat-completion->map [x]
  (#'openai/chat-completion->map x))

(defn- chat-chunk->map [x]
  (#'openai/chat-chunk->map x))

(defn- model-delete-params [id]
  (#'openai/->model-delete-params id))

(defn- chat-completion-update-params [id opts]
  (#'openai/->chat-completion-update-params id opts))

(defn- chat-completion-list-params [opts]
  (#'openai/->chat-completion-list-params opts))

(defn- chat-completion-message-list-params [id opts]
  (#'openai/->chat-completion-message-list-params id opts))

(defn- input-item-list-params [id opts]
  (#'openai/->input-item-list-params id opts))

(defn- stored-chat-message->map [x]
  (#'openai/stored-chat-message->map x))

(defn- deleted-model->map [x]
  (#'openai/deleted-model->map x))

(defn- deleted-chat-completion->map [x]
  (#'openai/deleted-chat-completion->map x))

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(defn- ex-data-for [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (ex-data e))))

(deftest builds-client-from-explicit-config
  (let [c (openai/client {:api-key "sk-test"
                          :organization "org-test"
                          :project "proj-test"
                          :base-url "https://example.test/v1"
                          :timeout-ms 1000
                          :max-retries 1})]
    (is (instance? OpenAIClient c))
    (.close ^OpenAIClient c)))

(defn- client-options ^com.openai.core.ClientOptions [^OpenAIClient c]
  (com.openai.client.OpenAIClientImpl/access$getClientOptions$p c))

(defn- private-field [obj field]
  (let [f (doto (.getDeclaredField (class obj) field)
            (.setAccessible true))]
    (.get f obj)))

(defn- subject-token-provider []
  (reify SubjectTokenProvider
    (tokenType [_] SubjectTokenType/JWT)
    (getToken [_ _ _] "token")
    (getTokenAsync [_ _ _] (java.util.concurrent.CompletableFuture/completedFuture "token"))))

(deftest advanced-client-options-reach-built-client
  (let [^ExecutorService executor (Executors/newSingleThreadExecutor)
        stream-executor (Executors/newSingleThreadExecutor)
        identity (-> (WorkloadIdentity/builder)
                     (.clientId "client")
                     (.identityProviderId "provider")
                     (.serviceAccountId "account")
                     (.provider (subject-token-provider))
                     (.build))
        c (openai/client {:api-key "sk-test"
                          :admin-api-key "admin-test"
                          :headers {"X-Test" "one"
                                    "X-Multi" ["a" "b"]}
                          :proxy {:host "proxy.example" :port 8080}
                          :executor executor
                          :stream-handler-executor stream-executor
                          :log-level :debug})
        workload-client (openai/client {:workload-identity identity})]
    (try
      (let [options (client-options c)
            headers (.headers options)
            retrying-client (private-field options "originalHttpClient")
            okhttp-client (private-field retrying-client "httpClient")
            okhttp (.getOkHttpClient$openai_java_client_okhttp okhttp-client)]
        (is (= "admin-test" (opt (.adminApiKey options))))
        (is (= ["one"] (.values headers "X-Test")))
        (is (= ["a" "b"] (.values headers "X-Multi")))
        (is (= LogLevel/DEBUG (.logLevel options)))
        (is (instance? com.openai.credential.BearerTokenCredential
                       (.credential options)))
        (is (= (Proxy$Type/HTTP) (.type (.proxy okhttp))))
        (is (= "proxy.example" (.getHostString ^InetSocketAddress (.address (.proxy okhttp)))))
        (is (= 8080 (.getPort ^InetSocketAddress (.address (.proxy okhttp)))))
        (is (identical? executor (.executorService (.dispatcher okhttp))))
        (is (identical? stream-executor
                       (private-field (.streamHandlerExecutor options)
                                      "executorService"))))
      (finally
        (.close ^OpenAIClient c)
        (is (instance? com.openai.credential.WorkloadIdentityCredential
                       (.credential (client-options workload-client))))
        (.close ^OpenAIClient workload-client)
        (.shutdownNow executor)
        (.shutdownNow ^java.util.concurrent.ExecutorService stream-executor)))))

(deftest validates-advanced-client-options
  (doseq [[opts message]
          [[{:admin-api-key 1} "admin-api-key"]
           [{:headers {"X-Test" 1}} "headers"]
           [{:proxy {:host "proxy.example" :port 0}} "proxy"]
           [{:log-level :verbose} "log-level"]
           [{:workload-identity {}} "workload-identity"]
           [{:executor :not-an-executor} "executor"]
           [{:stream-handler-executor :not-an-executor} "stream-handler-executor"]]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          (re-pattern message)
                          (openai/client (merge {:api-key "sk-test"} opts)))))
  (is (thrown? clojure.lang.ExceptionInfo
               (openai/client {:api-key "sk-test" :proxy {:host "proxy"}}))))

(deftest accepts-java-proxy
  (let [proxy (Proxy. Proxy$Type/HTTP (InetSocketAddress/createUnresolved "proxy.example" 8080))
        c (openai/client {:api-key "sk-test" :proxy proxy})]
    (is (instance? OpenAIClient c))
    (.close ^OpenAIClient c)))

(deftest translates-input-item-list-options
  (let [p (input-item-list-params
           "resp_123"
           {:after "item_10"
            :include [:message.output-text.logprobs]
            :limit 20
            :order :desc})]
    (is (instance? InputItemListParams p))
    (is (= "resp_123" (opt (.responseId p))))
    (is (= "item_10" (opt (.after p))))
    (is (= ["message.output_text.logprobs"]
           (mapv #(.asString ^ResponseIncludable %)
                 (opt (.include p)))))
    (is (= 20 (opt (.limit p))))
    (is (= "desc" (.asString ^InputItemListParams$Order (opt (.order p)))))))

(deftest input-item-list-params-defaults-to-the-response-id-alone
  (let [p (input-item-list-params "resp_123" {})]
    (is (= "resp_123" (opt (.responseId p))))
    (is (not (.isPresent (.after p))))
    (is (not (.isPresent (.limit p))))
    (is (not (.isPresent (.order p))))))

(deftest translates-string-input
  (let [p (params {:model "gpt-5.2" :input "plain string"})]
    (is (= "gpt-5.2" (.asString (opt (.model p)))))
    (is (= "plain string" (.asText (opt (.input p)))))))

(deftest translates-message-vector-input
  (let [p (params {:model "gpt-5.2"
                   :input [{:role :system :content "sys"}
                           {:role :developer :content "dev"}
                           {:role :user :content "hi"}
                           {:role :assistant :content "there"}]})
        input (opt (.input p))
        items (.asResponse input)]
    (is (= ["system" "developer" "user" "assistant"]
           (mapv #(-> ^ResponseInputItem %
                      .asEasyInputMessage
                      .role
                      .asString)
                 items)))
    (is (= ["sys" "dev" "hi" "there"]
           (mapv #(-> ^ResponseInputItem %
                      .asEasyInputMessage
                      .content
                      .asTextInput)
                 items)))))

(deftest translates-scalar-options
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :instructions "follow these"
                   :max-output-tokens 512
                   :max-tool-calls 7
                   :temperature 0.7
                   :top-p 0.9
                   :top-logprobs 3
                   :background true
                   :include [:web-search-call.action.sources
                             :message.output-text.logprobs]
                   :truncation :auto
                   :prompt-cache-key "cache-key"
                   :prompt-cache-options {:mode :standard :ttl :24h}
                   :safety-identifier "safe-user"
                   :service-tier :priority
                   :previous-response-id "resp_123"
                   :store false
                   :user "end-user-id"
                   :reasoning {:effort :medium :mode :pro}})]
    (is (= "follow these" (opt (.instructions p))))
    (is (= 512 (opt (.maxOutputTokens p))))
    (is (= 7 (opt (.maxToolCalls p))))
    (is (= 0.7 (opt (.temperature p))))
    (is (= 0.9 (opt (.topP p))))
    (is (= 3 (opt (.topLogprobs p))))
    (is (true? (opt (.background p))))
    (is (= ["web_search_call.action.sources" "message.output_text.logprobs"]
           (mapv #(.asString %) (opt (.include p)))))
    (is (= "auto" (.asString (opt (.truncation p)))))
    (is (= "cache-key" (opt (.promptCacheKey p))))
    (is (= "standard" (-> p .promptCacheOptions opt .mode opt .asString)))
    (is (= "24h" (-> p .promptCacheOptions opt .ttl opt .asString)))
    (is (= "safe-user" (opt (.safetyIdentifier p))))
    (is (= "priority" (.asString (opt (.serviceTier p)))))
    (is (= "resp_123" (opt (.previousResponseId p))))
    (is (false? (opt (.store p))))
    (is (= "end-user-id" (opt (.user p))))
    (is (= "medium" (-> p .reasoning opt .effort opt .asString)))
    (is (= "pro" (-> p .reasoning opt .mode opt .asString)))))

(deftest translates-metadata
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :metadata {:foo "bar" "baz" "quux"}})
        props (._additionalProperties (opt (.metadata p)))]
    (is (= "bar" (.asStringOrThrow (get props "foo"))))
    (is (= "quux" (.asStringOrThrow (get props "baz"))))))

(deftest translates-fast-service-tier
  (let [response-params (params {:model "gpt-5.2" :input "hi" :service-tier :fast})
        chat-params (chat-params {:model "gpt-4o-mini"
                                   :messages [{:role :user :content "hi"}]
                                   :service-tier :fast})]
    (is (= "fast" (.asString (opt (.serviceTier response-params)))))
    (is (= "fast" (.asString (opt (.serviceTier chat-params)))))))

(deftest translates-ultrafast-service-tier
  (let [response-params (params {:model "gpt-5.2" :input "hi" :service-tier :ultrafast})
        chat-params (chat-params {:model "gpt-4o-mini"
                                   :messages [{:role :user :content "hi"}]
                                   :service-tier :ultrafast})]
    (is (= "ultrafast" (.asString (opt (.serviceTier response-params)))))
    (is (= "ultrafast" (.asString (opt (.serviceTier chat-params)))))))

(deftest rejects-missing-required-keys
  (testing "missing model"
    (is (= {:openai/error :missing-key :key :model}
           (ex-data-for #(params {:input "hi"})))))
  (testing "missing input"
    (is (= {:openai/error :missing-key :key :input}
           (ex-data-for #(params {:model "gpt-5.2"})))))
  (testing "missing role"
    (is (= {:openai/error :missing-key :key :role}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input [{:content "hi"}]})))))
  (testing "missing content"
    (is (= {:openai/error :missing-key :key :content}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input [{:role :user}]}))))))

(deftest ignores-unknown-keys
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :unknown "ignored"})]
    (is (= "gpt-5.2" (.asString (opt (.model p)))))
    (is (= "hi" (.asText (opt (.input p)))))))

(deftest rejects-explicitly-empty-web-search-domain-allow-list
  (is (= {:openai/error :empty-allow-list :option :allowed-domains}
         (ex-data-for #(params {:model "gpt-5.2"
                                :input "hi"
                                :tools [{:type :web-search
                                         :allowed-domains []}]})))))

(deftest translates-tools
  (testing "function tool"
    (let [p (params {:model "gpt-5.2"
                     :input "hi"
                     :tools [{:type :function
                              :name "get_weather"
                              :description "Get the weather"
                              :strict true
                              :parameters {:type "object"
                                           :properties {:location {:type "string"}}
                                           :required ["location"]}}]})
          t (first (opt (.tools p)))
          f (.asFunction t)
          props (._additionalProperties (opt (.parameters f)))]
      (is (.isFunction t))
      (is (= "get_weather" (.name f)))
      (is (= "Get the weather" (opt (.description f))))
      (is (true? (opt (.strict f))))
      (is (= "object" (.asStringOrThrow (get props "type"))))
      (is (= ["location"] (.convert (get props "required") java.util.List)))))
  (testing "web search tool"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :web-search
                                                  :search-context-size :high
                                                  :user-location {:city "Denver"
                                                                  :country "US"
                                                                  :region "CO"
                                                                  :timezone "America/Denver"}
                                                  :allowed-domains ["example.com"]}]}))))]
      (is (.isWebSearch t))
      (is (= "high" (.asString (opt (.searchContextSize (.asWebSearch t))))))
      (is (= "Denver" (opt (.city (opt (.userLocation (.asWebSearch t)))))))
      (is (= ["example.com"]
             (vec (opt (.allowedDomains (opt (.filters (.asWebSearch t))))))))))
  (testing "file search tool"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :file-search
                                                  :vector-store-ids ["vs_1"]
                                                  :max-num-results 5
                                                  :filters {:type "eq"
                                                            :key "kind"
                                                            :value "docs"}
                                                  :ranking-options {:ranker "auto"
                                                                    :score-threshold 0.5}}]}))))]
      (is (.isFileSearch t))
      (is (= ["vs_1"] (vec (.vectorStoreIds (.asFileSearch t)))))
      (is (= 5 (opt (.maxNumResults (.asFileSearch t)))))
      (is (= "kind" (.key (.asComparisonFilter (opt (.filters (.asFileSearch t)))))))
      (is (= "auto" (.asString (opt (.ranker (opt (.rankingOptions (.asFileSearch t))))))))
      (is (= 0.5 (opt (.scoreThreshold (opt (.rankingOptions (.asFileSearch t)))))))))
  (testing "mcp tool"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :mcp
                                                  :server-label "docs"
                                                  :server-url "https://mcp.example.test"
                                                  :allowed-tools ["search"]
                                                  :require-approval :never
                                                  :headers {"X-Test" "1"}}]}))))
          m (.asMcp t)]
      (is (.isMcp t))
      (is (= "docs" (.serverLabel m)))
      (is (= "https://mcp.example.test" (opt (.serverUrl m))))
      (is (= ["search"] (vec (.asMcp (opt (.allowedTools m))))))
      (is (= "never" (.asString (.asMcpToolApprovalSetting (opt (.requireApproval m))))))
      (is (= "1" (.asStringOrThrow (get (._additionalProperties (opt (.headers m))) "X-Test"))))))
  (testing "rejects an explicitly empty MCP allow-list"
    (is (= {:openai/error :empty-allow-list :option :allowed-tools}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input "hi"
                                  :tools [{:type :mcp
                                           :server-label "docs"
                                           :allowed-tools []}]})))))
  (testing "code interpreter defaults to auto container"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :code-interpreter}]}))))]
      (is (.isCodeInterpreter t))
      (is (.isCodeInterpreterToolAuto (.container (.asCodeInterpreter t))))))
  (testing "code interpreter accepts container id"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :code-interpreter
                                                  :container "cntr_123"}]}))))]
      (is (= "cntr_123" (.asString (.container (.asCodeInterpreter t)))))))
  (testing "programmatic tool calling"
    (let [t (first (opt (.tools (params {:model "gpt-5.6-sol"
                                         :input "hi"
                                         :tools [{:type :programmatic-tool-calling}]}))))]
      (is (.isProgrammaticToolCalling t))))
  (testing "image generation"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :image-generation
                                                  :action :edit
                                                  :background :transparent
                                                  :output-format :png
                                                  :quality :high
                                                  :size "1024x1024"
                                                  :partial-images 2}]}))))
          image (.asImageGeneration t)]
      (is (.isImageGeneration t))
      (is (= "edit" (.asString (opt (.action image)))))
      (is (= "transparent" (.asString (opt (.background image)))))
      (is (= "png" (.asString (opt (.outputFormat image)))))
      (is (= "high" (.asString (opt (.quality image)))))
      (is (= "1024x1024" (.asString (opt (.size image)))))
      (is (= 2 (opt (.partialImages image))))))
  (testing "computer and local shell"
    (let [tools (opt (.tools (params {:model "gpt-5.2"
                                      :input "hi"
                                      :tools [{:type :computer}
                                              {:type :local-shell}]})))]
      (is (.isComputer (first tools)))
      (is (.isLocalShell (second tools)))))
  (testing "shell"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :shell
                                                  :environment :local}]}))))]
      (is (.isShell t))
      (is (.isLocal (opt (.environment (.asShell t)))))))
  (testing "apply patch"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :apply-patch}]}))))]
      (is (.isApplyPatch t))))
  (testing "custom"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :custom
                                                  :name "lint"
                                                  :description "Run lint"
                                                  :format :text}]}))))
          custom (.asCustom t)]
      (is (.isCustom t))
      (is (= "lint" (.name custom)))
      (is (= "Run lint" (opt (.description custom))))
      (is (.isText (opt (.format custom))))))
  (testing "tool search"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :tool-search
                                                  :description "Find a tool"
                                                  :execution :client
                                                  :parameters {:query "lint"}}]}))))
          search (.asSearch t)]
      (is (.isSearch t))
      (is (= "Find a tool" (opt (.description search))))
      (is (= "client" (.asString (opt (.execution search)))))
      (is (= "lint"
             (get (.convert (._parameters search) java.util.Map) "query")))))
  (testing "unknown tool type"
    (is (= {:openai/error :unknown-tool-type :type :bogus}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input "hi"
                                  :tools [{:type :bogus}]}))))))

(deftest translates-tool-choice
  (doseq [[choice expected] [[:auto ToolChoiceOptions/AUTO]
                             [:required ToolChoiceOptions/REQUIRED]
                             [:none ToolChoiceOptions/NONE]]]
    (let [tc (opt (.toolChoice (params {:model "gpt-5.2"
                                        :input "hi"
                                        :tool-choice choice})))]
      (is (.isOptions tc))
      (is (= expected (.asOptions tc)))))
  (let [^ResponseCreateParams$ToolChoice tc
        (opt (.toolChoice (params {:model "gpt-5.2"
                                   :input "hi"
                                   :tool-choice {:type :function
                                                 :name "get_weather"}})))]
    (is (.isFunction tc))
    (is (= "get_weather" (.name (.asFunction tc)))))
  (let [tc (opt (.toolChoice (params {:model "gpt-5.6-sol" :input "hi"
                                      :tool-choice {:type :programmatic-tool-calling}})))]
    (is (.isSpecificProgrammaticToolCallingParam tc))))

(deftest translates-parallel-tool-calls
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :parallel-tool-calls false})]
    (is (false? (opt (.parallelToolCalls p))))))

(deftest translates-prompt-context-management-and-cache-retention
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :prompt {:id "pmpt_weather"
                            :version "2"
                            :variables {:city "Denver"}}
                   :context-management [{:type :compaction
                                         :compact-threshold 1000}]
                   :prompt-cache-retention "24h"})
        prompt (opt (.prompt p))
        variables (opt (.variables prompt))
        context (first (opt (.contextManagement p)))]
    (is (= "pmpt_weather" (.id prompt)))
    (is (= "2" (opt (.version prompt))))
    (is (= "Denver"
           (.asStringOrThrow (get (._additionalProperties variables) "city"))))
    (is (= "compaction" (.type context)))
    (is (= 1000 (opt (.compactThreshold context))))
    (is (= "24h" (.asString (opt (.promptCacheRetention p)))))))

(deftest translates-json-schema-output-format
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :json-schema {:name "answer"
                                 :description "Structured answer"
                                 :strict true
                                 :schema {:type "object"
                                          :properties {:answer {:type "string"}}
                                          :required ["answer"]}}})
        cfg (-> p .text opt .format opt .asJsonSchema)
        props (._additionalProperties (.schema cfg))
        answer-props (-> (get props "properties")
                         (.convert java.util.Map)
                         (get "answer"))]
    (is (= "answer" (.name cfg)))
    (is (= "Structured answer" (opt (.description cfg))))
    (is (true? (opt (.strict cfg))))
    (is (= "object" (.asStringOrThrow (get props "type"))))
    (is (= "string" (get answer-props "type"))))
  (testing "missing name"
    (is (= {:openai/error :missing-key :key :name}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input "hi"
                                  :json-schema {:schema {:type "object"}}})))))
  (testing "missing schema"
    (is (= {:openai/error :missing-key :key :schema}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input "hi"
                                  :json-schema {:name "answer"}}))))))

(deftest parses-and-validates-structured-response-output
  (let [schema {:name "answer"
                :schema {:type "object"
                         :properties {:answer {:type "string"}
                                      :sources {:type "array"
                                                :items {:type "string"}}}
                         :required ["answer"]
                         :additionalProperties false}}]
    (is (= {:data {:answer "yes" :sources ["docs"]} :errors []}
           (openai/parse-structured-output
            {:text "{\"answer\":\"yes\",\"sources\":[\"docs\"]}"}
            schema)))
    (is (= [{:path [:answer] :error :type :expected "string" :actual "integer"}
            {:path [] :error :additional-property :key :extra}]
           (:errors (openai/parse-structured-output
                     {:text "{\"answer\":42,\"extra\":true}"}
                     schema))))
    (is (= :invalid-json
           (-> (openai/parse-structured-output {:text "{bad"} schema)
               :errors first :error)))))

(deftest rejects-structured-output-violating-common-json-schema-constraints
  (let [schema {:type "object"
                :properties {:name {:type "string" :minLength 3}
                             :score {:type "number" :minimum 1}}
                :required ["name" "score"]}]
    (is (= [{:path [:name] :error :min-length :minimum 3 :actual 2}
            {:path [:score] :error :minimum :minimum 1 :actual 0}]
           (:errors (openai/parse-structured-output
                     {:text "{\"name\":\"ok\",\"score\":0}"}
                     schema))))))

(deftest rejects-structured-output-violating-array-cardinality-constraints
  (let [schema {:type "array"
                :items {:type "string"}
                :minItems 1
                :maxItems 2}]
    (is (= [{:path [] :error :min-items :minimum 1 :actual 0}]
           (:errors (openai/parse-structured-output {:text "[]"} schema))))
    (is (= [{:path [] :error :max-items :maximum 2 :actual 3}]
           (:errors (openai/parse-structured-output {:text "[\"a\",\"b\",\"c\"]"} schema))))))

(deftest counts-structured-output-string-length-in-code-points
  (is (= []
         (:errors (openai/parse-structured-output
                   {:text "\"😀\""}
                   {:type "string" :maxLength 1}))))
  (is (= [{:path [] :error :min-length :minimum 2 :actual 1}]
         (:errors (openai/parse-structured-output
                   {:text "\"😀\""}
                   {:type "string" :minLength 2})))))

(deftest translates-input-token-count-params
  (let [p (input-token-count-params {:model "gpt-5.2"
                                     :input [{:role :user :content "hi"}]
                                     :instructions "count this"
                                     :previous-response-id "resp_123"
                                     :parallel-tool-calls false
                                     :reasoning {:effort :low}
                                     :tools [{:type :web-search}]
                                     :tool-choice {:type :function
                                                   :name "get_weather"}
                                     :truncation :auto
                                     :max-output-tokens 99
                                     :metadata {:ignored true}})
        item (first (.asResponseInputItems (opt (.input p))))]
    (is (= "gpt-5.2" (opt (.model p))))
    (is (= "count this" (opt (.instructions p))))
    (is (= "resp_123" (opt (.previousResponseId p))))
    (is (false? (opt (.parallelToolCalls p))))
    (is (= "low" (-> p .reasoning opt .effort opt .asString)))
    (is (.isWebSearch (first (opt (.tools p)))))
    (is (= "get_weather" (.name (.asFunction (opt (.toolChoice p))))))
    (is (= "auto" (.asString (opt (.truncation p)))))
    (is (.isEasyInputMessage item))))

(deftest translates-function-call-output-input
  (testing "optional name and namespace"
    (let [fco (.asFunctionCallOutput
               (first (.asResponse
                       (opt (.input
                             (params {:model "gpt-5.2"
                                      :input [{:type :function-call-output
                                               :call-id "call_123"
                                               :name "get_weather"
                                               :namespace "weather"
                                               :output "sunny"}]}))))))]
      (is (= "get_weather" (opt (.name fco))))
      (is (= "weather" (opt (.namespace fco))))))
  (testing "unset optional fields are omitted from the wire shape"
    (let [fco (.asFunctionCallOutput
               (first (.asResponse
                       (opt (.input
                             (params {:model "gpt-5.2"
                                      :input [{:type :function-call-output
                                               :call-id "call_123"
                                               :output "sunny"}]}))))))]
      (is (not (contains? (impl/sdk-object->clj fco) :name)))
      (is (not (contains? (impl/sdk-object->clj fco) :namespace)))))
  (testing "string output"
    (let [p (params {:model "gpt-5.2"
                     :input [{:type :function-call-output
                              :call-id "call_123"
                              :output "sunny"}]})
          item (first (.asResponse (opt (.input p))))
          fco (.asFunctionCallOutput item)]
      (is (.isFunctionCallOutput item))
      (is (= "call_123" (.callId fco)))
      (is (= "sunny" (.asString (.output fco))))))
  (testing "map output is encoded as JSON"
    (let [p (params {:model "gpt-5.2"
                     :input [{:type :function-call-output
                              :call-id "call_123"
                              :output {:forecast "sunny"}}]})
          fco (.asFunctionCallOutput (first (.asResponse (opt (.input p)))))]
      (is (= "{\"forecast\":\"sunny\"}" (.asString (.output fco)))))))

(deftest maps-function-call-output-input-item
  (testing "optional name and namespace are read when present"
    (let [item (ResponseItem/ofFunctionCallOutput
                (-> (ResponseFunctionToolCallOutputItem/builder)
                    (.id "item_123")
                    (.callId "call_123")
                    (.output "sunny")
                    (.name "get_weather")
                    (.namespace "weather")
                    (.status (ResponseFunctionToolCallOutputItem$Status/of "completed"))
                    (.build)))]
      (is (= {:name "get_weather" :namespace "weather"}
             (select-keys (response-item->map item) [:name :namespace])))))
  (testing "unset optional fields are omitted"
    (let [item (ResponseItem/ofFunctionCallOutput
                (-> (ResponseFunctionToolCallOutputItem/builder)
                    (.id "item_123")
                    (.callId "call_123")
                    (.output "sunny")
                    (.status (ResponseFunctionToolCallOutputItem$Status/of "completed"))
                    (.build)))
          m (response-item->map item)]
      (is (not (contains? m :name)))
      (is (not (contains? m :namespace)))))
  (testing "string output is unwrapped, not stringified through the union"
    (let [item (ResponseItem/ofFunctionCallOutput
                (-> (ResponseFunctionToolCallOutputItem/builder)
                    (.id "item_123")
                    (.callId "call_123")
                    (.output "sunny")
                    (.status (ResponseFunctionToolCallOutputItem$Status/of "completed"))
                    (.build)))]
      (is (= "sunny" (:output (response-item->map item))))))

  (testing "string output round-trips back into a request unchanged"
    (let [item (ResponseItem/ofFunctionCallOutput
                (-> (ResponseFunctionToolCallOutputItem/builder)
                    (.id "item_123")
                    (.callId "call_123")
                    (.output "sunny")
                    (.status (ResponseFunctionToolCallOutputItem$Status/of "completed"))
                    (.build)))
          read-back (response-item->map item)
          fco (.asFunctionCallOutput
               (first (.asResponse
                       (opt (.input (params {:model "gpt-5.2"
                                             :input [(select-keys read-back
                                                                  [:type :call-id :output])]}))))))]
      (is (= "sunny" (.asString (.output fco))))))
  (testing "content-list output is structured, not stringified through the union"
    (let [part (ResponseFunctionToolCallOutputItem$Output$FunctionAndCustomToolCallOutput/ofInputText
                (-> (ResponseInputText/builder) (.text "sunny") (.build)))
          item (ResponseItem/ofFunctionCallOutput
                (-> (ResponseFunctionToolCallOutputItem/builder)
                    (.id "item_123")
                    (.callId "call_123")
                    (.output (ResponseFunctionToolCallOutputItem$Output/ofContentList [part]))
                    (.status (ResponseFunctionToolCallOutputItem$Status/of "completed"))
                    (.build)))
          out (:output (response-item->map item))]
      (is (not (string? out)))
      (is (= "sunny" (-> out first :text))))))

(deftest translates-agent-tool-output-inputs
  (let [p (params {:model "gpt-5.2"
                   :input [{:type :computer-call-output
                            :call-id "call_computer"
                            :output {:image-url "data:image/png;base64,abc"}
                            :acknowledged-safety-checks [{:id "safe_1"
                                                          :code "policy"
                                                          :message "approved"}]}
                           {:type :local-shell-call-output
                            :id "shell_1"
                            :output "ok"
                            :status :completed}
                           {:type :shell-call-output
                            :call-id "call_shell"
                            :output [{:stdout "ok" :stderr "" :exit-code 0}]}
                           {:type :apply-patch-call-output
                            :call-id "call_patch"
                            :status :completed
                            :output "done"}
                           {:type :custom-tool-call-output
                            :call-id "call_custom"
                            :output {:ok true}}
                           {:type :tool-search-output
                            :call-id "call_search"
                            :execution :client
                            :status :completed
                            :tools [{:type :custom :name "lint"}]}
                           {:type :mcp-approval-response
                            :approval-request-id "approval_1"
                            :approve true
                            :reason "trusted"}]})
        xs (.asResponse (opt (.input p)))
        computer (.asComputerCallOutput (nth xs 0))
        local-shell (.asLocalShellCallOutput (nth xs 1))
        shell (.asShellCallOutput (nth xs 2))
        patch (.asApplyPatchCallOutput (nth xs 3))
        custom (.asCustomToolCallOutput (nth xs 4))
        search (.asToolSearchOutput (nth xs 5))
        approval (.asMcpApprovalResponse (nth xs 6))]
    (is (= "data:image/png;base64,abc" (opt (.imageUrl (.output computer)))))
    (is (= "safe_1" (-> computer .acknowledgedSafetyChecks opt first .id)))
    (is (= "ok" (.output local-shell)))
    (is (= 0 (-> shell .output first .outcome .asExit .exitCode)))
    (is (= "done" (opt (.output patch))))
    (is (= "{\"ok\":true}" (.asString (.output custom))))
    (is (.isCustom (first (.tools search))))
    (is (true? (.approve approval)))
    (is (= "trusted" (opt (.reason approval))))))

(defn- message-content-list [p]
  (-> ^ResponseCreateParams p
      .input
      opt
      .asResponse
      first
      .asEasyInputMessage
      .content
      .asResponseInputMessageContentList))

(deftest translates-multimodal-message-content
  (testing "mixed text and image url"
    (let [parts (message-content-list
                 (params {:model "gpt-5.2"
                          :input [{:role :user
                                   :content [{:type :text :text "look"}
                                             {:type :image
                                              :image-url "https://example.test/cat.png"
                                              :detail :high}]}]}))
          text-part (.asInputText ^ResponseInputContent (first parts))
          image-part (.asInputImage ^ResponseInputContent (second parts))]
      (is (= "look" (.text text-part)))
      (is (= "https://example.test/cat.png" (opt (.imageUrl image-part))))
      (is (= "high" (.asString (.detail image-part))))))
  (testing "image file id"
    (let [image-part (-> (message-content-list
                          (params {:model "gpt-5.2"
                                   :input [{:role :user
                                            :content [{:type :image
                                                       :file-id "file_123"
                                                       :detail :low}]}]}))
                         first
                         .asInputImage)]
      (is (= "file_123" (opt (.fileId image-part))))
      (is (= "low" (.asString (.detail image-part))))))
  (testing "file part"
    (let [file-part (-> (message-content-list
                         (params {:model "gpt-5.2"
                                  :input [{:role :user
                                           :content [{:type :file
                                                      :filename "paper.pdf"
                                                      :file-data "data:application/pdf;base64,AAAA"}]}]}))
                        first
                        .asInputFile)]
      (is (= "paper.pdf" (opt (.filename file-part))))
      (is (= "data:application/pdf;base64,AAAA" (opt (.fileData file-part))))))
  (testing "unknown part type"
    (is (= {:openai/error :unknown-content-type :type :audio}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input [{:role :user
                                           :content [{:type :audio
                                                      :text "hi"}]}]}))))))

(defn- text-content
  ([s] (text-content s []))
  ([s annotations]
  (ResponseOutputMessage$Content/ofOutputText
   (-> (ResponseOutputText/builder)
       (.text s)
       (.annotations annotations)
       (.build)))))

(defn- refusal-content [s]
  (ResponseOutputMessage$Content/ofRefusal
   (-> (ResponseOutputRefusal/builder)
       (.refusal s)
       (.build))))

(defn- message-item []
  (ResponseOutputItem/ofMessage
   (-> (ResponseOutputMessage/builder)
       (.id "msg_1")
       (.status ResponseOutputMessage$Status/COMPLETED)
       (.content [(text-content "Hello, ")
                  (text-content "world")
                  (refusal-content "nope")])
       (.build))))

(defn- function-call-item [args]
  (ResponseOutputItem/ofFunctionCall
   (-> (ResponseFunctionToolCall/builder)
       (.id "fc_1")
       (.callId "call_123")
       (.name "get_weather")
       (.arguments args)
       (.build))))

(deftest round-trips-output-function-call-as-input
  (let [m (output-item->map (function-call-item "{\"city\":\"Denver\"}"))
        rebuilt (openai/response-input-item m)]
    (is (.isFunctionCall rebuilt))
    (is (= "call_123" (.callId (.asFunctionCall rebuilt))))
    (is (= "{\"city\":\"Denver\"}"
           (.arguments (.asFunctionCall rebuilt))))))

(deftest round-trips-output-message-as-input
  (let [m (output-item->map (message-item))
        rebuilt (openai/response-input-item m)]
    (is (.isResponseOutputMessage rebuilt))
    (is (= "msg_1" (.id (.asResponseOutputMessage rebuilt))))))

(defn- unknown-item []
  (ResponseOutputItem/ofImageGenerationCall
   (-> (ResponseOutputItem$ImageGenerationCall/builder)
       (.id "img_1")
       (.result "base64")
       (.status (ResponseOutputItem$ImageGenerationCall$Status/of "completed"))
       (.build))))

(defn- url-annotation []
  (ResponseOutputText$Annotation/ofUrlCitation
   (-> (ResponseOutputText$Annotation$UrlCitation/builder)
       (.url "https://example.test")
       (.title "Example")
       (.startIndex 0)
       (.endIndex 5)
       (.build))))

(defn- logprob []
  (-> (ResponseOutputText$Logprob/builder)
      (.token "Hello")
      (.bytes [72 101 108 108 111])
      (.logprob -0.1)
      (.topLogprobs [(-> (ResponseOutputText$Logprob$TopLogprob/builder)
                         (.token "Hi")
                         (.bytes [72 105])
                         (.logprob -0.5)
                         (.build))])
      (.build)))

(defn- reasoning-item []
  (ResponseOutputItem/ofReasoning
   (-> (ResponseReasoningItem/builder)
       (.id "rs_1")
       (.status ResponseReasoningItem$Status/COMPLETED)
       (.summary [(-> (ResponseReasoningItem$Summary/builder)
                      (.text "short thought")
                      (.build))])
       (.build))))

(defn- mcp-call-item []
  (ResponseOutputItem/ofMcpCall
   (-> (ResponseOutputItem$McpCall/builder)
       (.id "mcp_1")
       (.name "search")
       (.serverLabel "docs")
       (.arguments "{\"q\":\"sdk\"}")
       (.output "result")
       (.status ResponseOutputItem$McpCall$Status/COMPLETED)
       (.build))))

(deftest maps-mcp-call-error-as-structured-data
  (let [error (McpToolCallError/ofProtocol
               (-> (McpToolCallError$McpProtocolError/builder)
                   (.code 100)
                   (.message "invalid request")
                   (.build)))
        item (ResponseOutputItem/ofMcpCall
              (-> (ResponseOutputItem$McpCall/builder)
                  (.id "mcp_1")
                  (.name "search")
                  (.serverLabel "docs")
                  (.arguments "{}")
                  (.error (java.util.Optional/of error))
                  (.status ResponseOutputItem$McpCall$Status/FAILED)
                  (.build)))
        m (output-item->map item)]
    (is (= {:type "mcp_protocol_error"
            :code 100
            :message "invalid request"}
           (:error m)))))

(defn- custom-tool-call-item []
  (ResponseOutputItem/ofCustomToolCall
   (-> (ResponseCustomToolCall/builder)
       (.id "ctc_1")
       (.callId "call_custom")
       (.name "lint")
       (.input "src")
       (.build))))

(defn- local-shell-call-item []
  (ResponseOutputItem/ofLocalShellCall
   (-> (ResponseOutputItem$LocalShellCall/builder)
       (.id "shell_1")
       (.action (-> (ResponseOutputItem$LocalShellCall$Action/builder)
                    (.command ["pwd"])
                    (.env (-> (ResponseOutputItem$LocalShellCall$Action$Env/builder)
                              (.build)))
                    (.build)))
       (.callId "call_shell")
       (.status ResponseOutputItem$LocalShellCall$Status/COMPLETED)
       (.build))))

(defn- computer-call-item []
  (ResponseOutputItem/ofComputerCall
   (-> (ResponseComputerToolCall/builder)
       (.id "comp_1")
       (.callId "call_comp")
       (.pendingSafetyChecks [])
       (.status ResponseComputerToolCall$Status/COMPLETED)
       (.type ResponseComputerToolCall$Type/COMPUTER_CALL)
       (.build))))

(defn- response [items]
  (-> (com.openai.models.responses.Response/builder)
      (.id "resp_123")
      (.model "gpt-5.2")
      (.createdAt 1234.5)
      (.background (java.util.Optional/empty))
      (.completedAt (java.util.Optional/empty))
      (.conversation (java.util.Optional/empty))
      (.error (java.util.Optional/empty))
      (.incompleteDetails (java.util.Optional/empty))
      (.instructions (java.util.Optional/empty))
      (.maxOutputTokens (java.util.Optional/empty))
      (.metadata (java.util.Optional/empty))
      (.parallelToolCalls false)
      (.previousResponseId (java.util.Optional/empty))
      (.reasoning (java.util.Optional/empty))
      (.status ResponseStatus/COMPLETED)
      (.temperature (java.util.Optional/empty))
      (.toolChoice ToolChoiceOptions/AUTO)
      (.tools [])
      (.topP (java.util.Optional/empty))
      (.truncation (java.util.Optional/empty))
      (.output items)
      (.usage (-> (ResponseUsage/builder)
                  (.inputTokens 10)
                  (.inputTokensDetails (-> (ResponseUsage$InputTokensDetails/builder)
                                           (.cacheWriteTokens 0)
                                           (.cachedTokens 0)
                                           (.build)))
                  (.outputTokens 20)
                  (.outputTokensDetails (-> (ResponseUsage$OutputTokensDetails/builder)
                                            (.reasoningTokens 0)
                                            (.build)))
                  (.totalTokens 30)
                  (.build)))
      (.build)))

(defn- incomplete-response [items]
  (-> (response items)
      .toBuilder
      (.status ResponseStatus/INCOMPLETE)
      (.incompleteDetails (-> (Response$IncompleteDetails/builder)
                              (.reason Response$IncompleteDetails$Reason/MAX_OUTPUT_TOKENS)
                              (.build)))
      (.build)))

(deftest maps-function-call-output-response-item
  (let [item (ResponseOutputItem/ofFunctionCallOutput
              (-> (ResponseFunctionToolCallOutputItem/builder)
                  (.id "item_123")
                  (.callId "call_123")
                  (.output "sunny")
                  (.status (ResponseFunctionToolCallOutputItem$Status/of "completed"))
                  (.name "get_weather")
                  (.namespace "weather")
                  (.build)))
        m (-> (response->map (response [item])) :output first)]
    (is (= {:name "get_weather" :namespace "weather"}
           (select-keys m [:name :namespace])))))

(deftest maps-response-to-clojure
  (let [m (response->map (response [(message-item)
                                    (function-call-item "{\"location\":\"Denver\"}")
                                    (unknown-item)
                                    (reasoning-item)
                                    (mcp-call-item)
                                    (custom-tool-call-item)
                                    (local-shell-call-item)
                                    (computer-call-item)]))
        rebuilt (mapv openai/response-input-item (:output m))]
    (is (= "resp_123" (:id m)))
    (is (= "gpt-5.2" (:model m)))
    (is (= :completed (:status m)))
    (is (= 1234.5 (:created-at m)))
    (is (= {:input-tokens 10
            :input-tokens-details {:cache-write-tokens 0 :cached-tokens 0}
            :output-tokens 20 :total-tokens 30}
           (:usage m)))
    (is (= "Hello, world" (:text m)))
    (is (= [{:type :message
             :role :assistant
             :id "msg_1"
             :content [{:type :text :text "Hello, "}
                       {:type :text :text "world"}
                       {:type :refusal :refusal "nope"}]}
            {:type :function-call
             :name "get_weather"
             :call-id "call_123"
             :id "fc_1"
             :arguments {:location "Denver"}}
            {:type :image-generation-call
             :id "img_1"
             :status :completed
             :result "base64"}
            {:type :reasoning
             :id "rs_1"
             :status :completed
             :summary ["short thought"]}
            {:type :mcp-call
             :id "mcp_1"
             :status :completed
             :name "search"
             :server-label "docs"
             :arguments "{\"q\":\"sdk\"}"
             :output "result"}
            {:type :custom-tool-call
             :id "ctc_1"
             :name "lint"
             :input "src"
             :call-id "call_custom"}
            {:type :local-shell-call
             :id "shell_1"
             :status :completed
             :call-id "call_shell"
             :action {:command ["pwd"] :env {} :type "exec"}}
            {:type :computer-call
             :id "comp_1"
             :status :completed
             :call-id "call_comp"
             :pending-safety-checks []}]
           (:output m)))
    (is (.isResponseOutputMessage (nth rebuilt 0)))
    (is (.isFunctionCall (nth rebuilt 1)))
    (is (.isImageGenerationCall (nth rebuilt 2)))
    (is (.isReasoning (nth rebuilt 3)))
    (is (.isMcpCall (nth rebuilt 4)))
    (is (.isCustomToolCall (nth rebuilt 5)))
    (is (.isLocalShellCall (nth rebuilt 6)))
    (is (.isComputerCall (nth rebuilt 7)))))

(deftest maps-response-prompt-fields
  (let [prompt (-> (com.openai.models.responses.ResponsePrompt/builder)
                   (.id "pmpt_weather")
                   (.version "2")
                   (.build))
        r (-> (response [])
              .toBuilder
              (.prompt (java.util.Optional/of prompt))
              (.promptCacheRetention
               (java.util.Optional/of
                (com.openai.models.responses.Response$PromptCacheRetention/of "24h")))
              (.build))
        m (response->map r)]
    (is (= "pmpt_weather" (get-in m [:prompt :id])))
    (is (= "2" (get-in m [:prompt :version])))
    (is (= "24h" (:prompt-cache-retention m)))))

(deftest maps-agent-output-items-losslessly
  (let [web (ResponseOutputItem/ofWebSearchCall
             (-> (com.openai.models.responses.ResponseFunctionWebSearch/builder)
                 (.id "web_1")
                 (.action (-> (com.openai.models.responses.ResponseFunctionWebSearch$Action$Search/builder)
                              (.query "openai-java")
                              (.queries ["openai-java"])
                              (.sources [])
                              (.build)))
                 (.status (com.openai.models.responses.ResponseFunctionWebSearch$Status/of "completed"))
                 (.build)))
        file (ResponseOutputItem/ofFileSearchCall
              (-> (com.openai.models.responses.ResponseFileSearchToolCall/builder)
                  (.id "file_search_1")
                  (.queries ["responses"])
                  (.status (com.openai.models.responses.ResponseFileSearchToolCall$Status/of "completed"))
                  (.results [(-> (com.openai.models.responses.ResponseFileSearchToolCall$Result/builder)
                                 (.fileId "file_1")
                                 (.filename "guide.md")
                                 (.score (float 0.9))
                                 (.text "result")
                                 (.build))])
                  (.build)))
        code (ResponseOutputItem/ofCodeInterpreterCall
              (-> (com.openai.models.responses.ResponseCodeInterpreterToolCall/builder)
                  (.id "code_1")
                  (.code "(+ 1 2)")
                  (.containerId "container_1")
                  (.addLogsOutput "3")
                  (.status (com.openai.models.responses.ResponseCodeInterpreterToolCall$Status/of "completed"))
                  (.build)))
        mcp-tools (ResponseOutputItem/ofMcpListTools
                   (-> (ResponseOutputItem$McpListTools/builder)
                       (.id "mcp_tools_1")
                       (.serverLabel "docs")
                       (.tools [(-> (ResponseOutputItem$McpListTools$Tool/builder)
                                    (.name "search")
                                    (.description "Search docs")
                                    (.inputSchema (com.openai.core.JsonValue/from {"type" "object"}))
                                    (.annotations (com.openai.core.JsonValue/from {"readOnly" true}))
                                    (.build))])
                       (.build)))
        shell (ResponseOutputItem/ofShellCall
               (-> (com.openai.models.responses.ResponseFunctionShellToolCall/builder)
                   (.id "shell_2")
                   (.callId "call_shell_2")
                   (.action (-> (com.openai.models.responses.ResponseFunctionShellToolCall$Action/builder)
                                (.commands ["pwd" "ls"])
                                (.timeoutMs 1000)
                                (.maxOutputLength 4096)
                                (.build)))
                   (.environment (-> (com.openai.models.responses.ResponseLocalEnvironment/builder)
                                     (.build)))
                   (.status (com.openai.models.responses.ResponseFunctionShellToolCall$Status/of "completed"))
                   (.build)))
        patch (ResponseOutputItem/ofApplyPatchCall
               (-> (com.openai.models.responses.ResponseApplyPatchToolCall/builder)
                   (.id "patch_1")
                   (.callId "call_patch_1")
                   (.operation (-> (com.openai.models.responses.ResponseApplyPatchToolCall$Operation$UpdateFile/builder)
                                   (.path "src/core.clj")
                                   (.diff "@@")
                                   (.build)))
                   (.status (com.openai.models.responses.ResponseApplyPatchToolCall$Status/of "completed"))
                   (.build)))
        custom-tool (-> (com.openai.models.responses.CustomTool/builder) (.name "lint") (.build))
        tool-output (ResponseOutputItem/ofToolSearchOutput
                     (-> (com.openai.models.responses.ResponseToolSearchOutputItem/builder)
                         (.id "tools_1")
                         (.callId "call_search_1")
                         (.execution (com.openai.models.responses.ResponseToolSearchOutputItem$Execution/of "client"))
                         (.status (com.openai.models.responses.ResponseToolSearchOutputItem$Status/of "completed"))
                         (.tools [(com.openai.models.responses.Tool/ofCustom custom-tool)])
                         (.build)))
        output (:output (response->map (response [web file code mcp-tools shell patch tool-output])))
        by-type (into {} (map (juxt :type identity)) output)
        rebuilt (mapv openai/response-input-item output)]
    (is (= {:type "search" :query "openai-java" :queries ["openai-java"] :sources []}
           (get-in by-type [:web-search-call :action])))
    (is (= ["responses"] (get-in by-type [:file-search-call :queries])))
    (is (= "guide.md" (get-in by-type [:file-search-call :results 0 :filename])))
    (is (= "(+ 1 2)" (get-in by-type [:code-interpreter-call :code])))
    (is (= "3" (get-in by-type [:code-interpreter-call :outputs 0 :logs])))
    (is (= "search" (get-in by-type [:mcp-list-tools :tools 0 :name])))
    (is (= ["pwd" "ls"] (get-in by-type [:shell-call :action :commands])))
    (is (= "src/core.clj" (get-in by-type [:apply-patch-call :operation :path])))
    (is (= "lint" (get-in by-type [:tool-search-output :tools 0 :name])))
    (is (.isWebSearchCall (nth rebuilt 0)))
    (is (.isFileSearchCall (nth rebuilt 1)))
    (is (.isCodeInterpreterCall (nth rebuilt 2)))
    (is (.isMcpListTools (nth rebuilt 3)))
    (is (.isShellCall (nth rebuilt 4)))
    (is (.isApplyPatchCall (nth rebuilt 5)))
    (is (.isToolSearchOutput (nth rebuilt 6)))))

(deftest maps-output-text-annotations
  (let [m (response->map
           (response [(ResponseOutputItem/ofMessage
                       (-> (ResponseOutputMessage/builder)
                           (.id "msg_1")
                           (.status ResponseOutputMessage$Status/COMPLETED)
                           (.content [(text-content "Hello" [(url-annotation)])])
                           (.build)))]))]
    (is (= [{:type :url-citation
             :url "https://example.test"
             :title "Example"
             :start-index 0
             :end-index 5}]
           (-> m :output first :content first :annotations)))))

(deftest maps-output-text-logprobs
  (let [content (ResponseOutputMessage$Content/ofOutputText
                 (-> (ResponseOutputText/builder)
                     (.text "Hello")
                     (.annotations [])
                     (.logprobs [(logprob)])
                     (.build)))
        item (ResponseOutputItem/ofMessage
              (-> (ResponseOutputMessage/builder)
                  (.id "msg_1")
                  (.status ResponseOutputMessage$Status/COMPLETED)
                  (.content [content])
                  (.build)))
        m (response->map (response [item]))]
    (is (= [{:token "Hello"
             :bytes [72 101 108 108 111]
             :logprob -0.1
             :top-logprobs [{:token "Hi"
                             :bytes [72 105]
                             :logprob -0.5}]}]
           (-> m :output first :content first :logprobs)))))

(deftest maps-incomplete-details
  (let [m (response->map (incomplete-response []))]
    (is (= {:reason :max-output-tokens} (:incomplete-details m)))))

(deftest response-map-keeps-garbage-arguments-raw
  (let [m (response->map (response [(function-call-item "{not json}")]))]
    (is (= "{not json}" (-> m :output first :arguments)))
    (is (= "" (:text m)))))

(deftest response-map-preserves-unknown-fields-when-lossless
  (let [r (-> (response [])
              .toBuilder
              (.putAdditionalProperty "future_field" (JsonValue/from "preserved"))
              (.build))
        curated (response->map r)
        lossless (response->map-with-options r {:lossless? true})]
    (is (= {:id "resp_123"
            :model "gpt-5.2"
            :output []
            :text ""
            :created-at 1234.5
            :status :completed
            :usage {:input-tokens 10
                    :input-tokens-details {:cache-write-tokens 0 :cached-tokens 0}
                    :output-tokens 20
                    :total-tokens 30}}
           curated))
    (is (= "preserved" (get-in lossless [:openai/raw :future-field])))))

(deftest maps-model-to-clojure
  (let [m (model->map (-> (Model/builder)
                          (.id "gpt-5.2")
                          (.created 1790000000)
                          (.ownedBy "openai")
                          (.build)))]
    (is (= {:id "gpt-5.2"
            :created 1790000000
            :owned-by "openai"}
           m))))

(deftest maps-stream-text-events-to-clojure
  (is (= {:type :output-text-delta
          :delta "Hel"
          :item-id "msg_1"
          :output-index 0}
         (event->map
          (ResponseStreamEvent/ofOutputTextDelta
           (-> (ResponseTextDeltaEvent/builder)
               (.contentIndex 0)
               (.delta "Hel")
               (.itemId "msg_1")
               (.logprobs [])
               (.outputIndex 0)
               (.sequenceNumber 1)
               (.build))))))
  (is (= {:type :output-text-done
          :text "Hello"
          :item-id "msg_1"
          :output-index 0}
         (event->map
          (ResponseStreamEvent/ofOutputTextDone
           (-> (ResponseTextDoneEvent/builder)
               (.contentIndex 0)
               (.itemId "msg_1")
               (.logprobs [])
               (.outputIndex 0)
               (.sequenceNumber 2)
               (.text "Hello")
               (.build)))))))

(deftest maps-stream-function-call-events-to-clojure
  (is (= {:type :function-call-arguments-delta
          :delta "{\""
          :item-id "fc_1"}
         (event->map
          (ResponseStreamEvent/ofFunctionCallArgumentsDelta
           (-> (ResponseFunctionCallArgumentsDeltaEvent/builder)
               (.delta "{\"")
               (.itemId "fc_1")
               (.outputIndex 1)
               (.sequenceNumber 3)
               (.build))))))
  (is (= {:type :function-call-arguments-done
          :arguments "{\"location\":\"Denver\"}"
          :item-id "fc_1"}
         (event->map
          (ResponseStreamEvent/ofFunctionCallArgumentsDone
           (-> (ResponseFunctionCallArgumentsDoneEvent/builder)
               (.arguments "{\"location\":\"Denver\"}")
               (.itemId "fc_1")
               (.name "get_weather")
               (.outputIndex 1)
               (.sequenceNumber 4)
               (.build)))))))

(deftest maps-stream-reasoning-and-refusal-events-to-clojure
  (is (= {:type :reasoning-text-delta :delta "think"}
         (event->map
          (ResponseStreamEvent/ofReasoningTextDelta
           (-> (ResponseReasoningTextDeltaEvent/builder)
               (.contentIndex 0)
               (.delta "think")
               (.itemId "rs_1")
               (.outputIndex 0)
               (.sequenceNumber 5)
               (.build))))))
  (is (= {:type :reasoning-text-done :text "thought"}
         (event->map
          (ResponseStreamEvent/ofReasoningTextDone
           (-> (ResponseReasoningTextDoneEvent/builder)
               (.contentIndex 0)
               (.itemId "rs_1")
               (.outputIndex 0)
               (.sequenceNumber 6)
               (.text "thought")
               (.build))))))
  (is (= {:type :refusal-delta :delta "no"}
         (event->map
          (ResponseStreamEvent/ofRefusalDelta
           (-> (ResponseRefusalDeltaEvent/builder)
               (.contentIndex 0)
               (.delta "no")
               (.itemId "msg_1")
               (.outputIndex 0)
               (.sequenceNumber 7)
               (.build))))))
  (is (= {:type :refusal-done :refusal "nope"}
         (event->map
          (ResponseStreamEvent/ofRefusalDone
           (-> (ResponseRefusalDoneEvent/builder)
               (.contentIndex 0)
               (.itemId "msg_1")
               (.outputIndex 0)
               (.refusal "nope")
               (.sequenceNumber 8)
               (.build)))))))

(deftest maps-stream-output-item-events-to-clojure
  (is (= {:type :output-item-added
          :item {:type :function-call
                 :name "get_weather"
                 :call-id "call_123"
                 :id "fc_1"
                 :arguments {:location "Denver"}}
          :output-index 1}
         (event->map
          (ResponseStreamEvent/ofOutputItemAdded
           (-> (ResponseOutputItemAddedEvent/builder)
               (.item (function-call-item "{\"location\":\"Denver\"}"))
               (.outputIndex 1)
               (.sequenceNumber 9)
               (.build))))))
  (is (= {:type :output-item-done
          :item {:type :image-generation-call
                 :id "img_1"
                 :status :completed
                 :result "base64"}
          :output-index 2}
         (event->map
          (ResponseStreamEvent/ofOutputItemDone
           (-> (ResponseOutputItemDoneEvent/builder)
               (.item (unknown-item))
               (.outputIndex 2)
               (.sequenceNumber 10)
               (.build)))))))

(deftest maps-stream-lifecycle-events-to-clojure
  (is (= {:type :created}
         (event->map
          (ResponseStreamEvent/ofCreated
           (-> (ResponseCreatedEvent/builder)
               (.response (response []))
               (.sequenceNumber 11)
               (.build))))))
  (is (= {:type :in-progress}
         (event->map
          (ResponseStreamEvent/ofInProgress
           (-> (ResponseInProgressEvent/builder)
               (.response (response []))
               (.sequenceNumber 12)
               (.build))))))
  (is (= :completed
         (:type
          (event->map
           (ResponseStreamEvent/ofCompleted
            (-> (ResponseCompletedEvent/builder)
                (.response (response [(message-item)]))
                (.sequenceNumber 13)
                (.build)))))))
  (is (= "Hello, world"
         (-> (event->map
              (ResponseStreamEvent/ofCompleted
               (-> (ResponseCompletedEvent/builder)
                   (.response (response [(message-item)]))
                   (.sequenceNumber 13)
                   (.build))))
             :response
             :text)))
  (is (= :incomplete
         (:type
          (event->map
           (ResponseStreamEvent/ofIncomplete
            (-> (ResponseIncompleteEvent/builder)
                (.response (response []))
                (.sequenceNumber 14)
                (.build)))))))
  (is (= :failed
         (:type
          (event->map
           (ResponseStreamEvent/ofFailed
            (-> (ResponseFailedEvent/builder)
                (.response (response []))
                (.sequenceNumber 15)
                (.build))))))))

(deftest maps-stream-error-and-other-events-to-clojure
  (is (= {:type :error
          :message "bad request"
          :code "invalid_request"}
         (event->map
          (ResponseStreamEvent/ofError
           (-> (ResponseErrorEvent/builder)
               (.code "invalid_request")
               (.message "bad request")
               (.param (java.util.Optional/empty))
               (.sequenceNumber 16)
               (.build))))))
  (is (= :queued
         (:type
         (event->map
          (ResponseStreamEvent/ofQueued
           (-> (com.openai.models.responses.ResponseQueuedEvent/builder)
               (.response (response []))
               (.sequenceNumber 17)
               (.build))))))))

(defn- response-stream-event [factory class-name fields]
  (let [cls (Class/forName (str "com.openai.models.responses." class-name))
        builder (clojure.lang.Reflector/invokeStaticMethod cls "builder" (object-array 0))]
    (when (some #(= "outputIndex" (.getName ^java.lang.reflect.Method %))
                (.getMethods (class builder)))
      (clojure.lang.Reflector/invokeInstanceMethod builder "outputIndex" (object-array [0])))
    (doseq [[method value] fields]
      (clojure.lang.Reflector/invokeInstanceMethod builder method (object-array [value])))
    (clojure.lang.Reflector/invokeStaticMethod
     ResponseStreamEvent factory
     (object-array [(clojure.lang.Reflector/invokeInstanceMethod builder "build" (object-array 0))]))))

(deftest maps-full-stream-event-union
  (let [simple-cases
        [["ofAudioDelta" "ResponseAudioDeltaEvent" [["delta" "abc"] ["sequenceNumber" 20]] :audio-delta]
         ["ofAudioDone" "ResponseAudioDoneEvent" [["sequenceNumber" 21]] :audio-done]
         ["ofAudioTranscriptDelta" "ResponseAudioTranscriptDeltaEvent" [["delta" "hi"] ["sequenceNumber" 22]] :audio-transcript-delta]
         ["ofAudioTranscriptDone" "ResponseAudioTranscriptDoneEvent" [["sequenceNumber" 23]] :audio-transcript-done]
         ["ofReasoningSummaryTextDelta" "ResponseReasoningSummaryTextDeltaEvent" [["delta" "sum"] ["itemId" "rs_1"] ["summaryIndex" 0] ["sequenceNumber" 24]] :reasoning-summary-text-delta]
         ["ofReasoningSummaryTextDone" "ResponseReasoningSummaryTextDoneEvent" [["text" "summary"] ["itemId" "rs_1"] ["summaryIndex" 0] ["sequenceNumber" 25]] :reasoning-summary-text-done]
         ["ofWebSearchCallInProgress" "ResponseWebSearchCallInProgressEvent" [["itemId" "web_1"] ["sequenceNumber" 26]] :web-search-call-in-progress]
         ["ofWebSearchCallSearching" "ResponseWebSearchCallSearchingEvent" [["itemId" "web_1"] ["sequenceNumber" 27]] :web-search-call-searching]
         ["ofWebSearchCallCompleted" "ResponseWebSearchCallCompletedEvent" [["itemId" "web_1"] ["sequenceNumber" 28]] :web-search-call-completed]
         ["ofFileSearchCallInProgress" "ResponseFileSearchCallInProgressEvent" [["itemId" "file_1"] ["sequenceNumber" 29]] :file-search-call-in-progress]
         ["ofFileSearchCallSearching" "ResponseFileSearchCallSearchingEvent" [["itemId" "file_1"] ["sequenceNumber" 30]] :file-search-call-searching]
         ["ofFileSearchCallCompleted" "ResponseFileSearchCallCompletedEvent" [["itemId" "file_1"] ["sequenceNumber" 31]] :file-search-call-completed]
         ["ofImageGenerationCallInProgress" "ResponseImageGenCallInProgressEvent" [["itemId" "img_1"] ["sequenceNumber" 32]] :image-generation-call-in-progress]
         ["ofImageGenerationCallGenerating" "ResponseImageGenCallGeneratingEvent" [["itemId" "img_1"] ["sequenceNumber" 33]] :image-generation-call-generating]
         ["ofImageGenerationCallCompleted" "ResponseImageGenCallCompletedEvent" [["itemId" "img_1"] ["sequenceNumber" 34]] :image-generation-call-completed]
         ["ofImageGenerationCallPartialImage" "ResponseImageGenCallPartialImageEvent" [["itemId" "img_1"] ["partialImageB64" "abc"] ["partialImageIndex" 0] ["sequenceNumber" 35]] :image-generation-call-partial-image]
         ["ofMcpCallArgumentsDelta" "ResponseMcpCallArgumentsDeltaEvent" [["delta" "{"] ["itemId" "mcp_1"] ["sequenceNumber" 36]] :mcp-call-arguments-delta]
         ["ofMcpCallArgumentsDone" "ResponseMcpCallArgumentsDoneEvent" [["arguments" "{}"] ["itemId" "mcp_1"] ["sequenceNumber" 37]] :mcp-call-arguments-done]
         ["ofMcpCallInProgress" "ResponseMcpCallInProgressEvent" [["itemId" "mcp_1"] ["sequenceNumber" 38]] :mcp-call-in-progress]
         ["ofMcpCallCompleted" "ResponseMcpCallCompletedEvent" [["itemId" "mcp_1"] ["sequenceNumber" 39]] :mcp-call-completed]
         ["ofMcpCallFailed" "ResponseMcpCallFailedEvent" [["itemId" "mcp_1"] ["sequenceNumber" 40]] :mcp-call-failed]
         ["ofMcpListToolsInProgress" "ResponseMcpListToolsInProgressEvent" [["itemId" "mcp_tools_1"] ["sequenceNumber" 41]] :mcp-list-tools-in-progress]
         ["ofMcpListToolsCompleted" "ResponseMcpListToolsCompletedEvent" [["itemId" "mcp_tools_1"] ["sequenceNumber" 42]] :mcp-list-tools-completed]
         ["ofMcpListToolsFailed" "ResponseMcpListToolsFailedEvent" [["itemId" "mcp_tools_1"] ["sequenceNumber" 43]] :mcp-list-tools-failed]
         ["ofCodeInterpreterCallCodeDelta" "ResponseCodeInterpreterCallCodeDeltaEvent" [["delta" "print"] ["itemId" "code_1"] ["sequenceNumber" 44]] :code-interpreter-call-code-delta]
         ["ofCodeInterpreterCallCodeDone" "ResponseCodeInterpreterCallCodeDoneEvent" [["code" "print(1)"] ["itemId" "code_1"] ["sequenceNumber" 45]] :code-interpreter-call-code-done]
         ["ofCodeInterpreterCallInProgress" "ResponseCodeInterpreterCallInProgressEvent" [["itemId" "code_1"] ["sequenceNumber" 46]] :code-interpreter-call-in-progress]
         ["ofCodeInterpreterCallInterpreting" "ResponseCodeInterpreterCallInterpretingEvent" [["itemId" "code_1"] ["sequenceNumber" 47]] :code-interpreter-call-interpreting]
         ["ofCodeInterpreterCallCompleted" "ResponseCodeInterpreterCallCompletedEvent" [["itemId" "code_1"] ["sequenceNumber" 48]] :code-interpreter-call-completed]
         ["ofCustomToolCallInputDelta" "ResponseCustomToolCallInputDeltaEvent" [["delta" "src"] ["itemId" "custom_1"] ["outputIndex" 0] ["sequenceNumber" 49]] :custom-tool-call-input-delta]
         ["ofCustomToolCallInputDone" "ResponseCustomToolCallInputDoneEvent" [["input" "src"] ["itemId" "custom_1"] ["outputIndex" 0] ["sequenceNumber" 50]] :custom-tool-call-input-done]]]
    (doseq [[factory class-name fields expected] simple-cases]
      (is (= expected (:type (event->map (response-stream-event factory class-name fields))))))
    (let [added-part (-> (com.openai.models.responses.ResponseContentPartAddedEvent/builder)
                         (.contentIndex 0) (.itemId "msg_1") (.outputIndex 0) (.refusalPart "no") (.sequenceNumber 51) (.build))
          done-part (-> (com.openai.models.responses.ResponseContentPartDoneEvent/builder)
                        (.contentIndex 0) (.itemId "msg_1") (.outputIndex 0) (.refusalPart "no") (.sequenceNumber 52) (.build))
          summary-added-part (-> (com.openai.models.responses.ResponseReasoningSummaryPartAddedEvent$Part/builder) (.text "sum") (.build))
          summary-done-part (-> (com.openai.models.responses.ResponseReasoningSummaryPartDoneEvent$Part/builder) (.text "summary") (.build))]
      (is (= :content-part-added (:type (event->map (ResponseStreamEvent/ofContentPartAdded added-part)))))
      (is (= :content-part-done (:type (event->map (ResponseStreamEvent/ofContentPartDone done-part)))))
      (is (= :reasoning-summary-part-added
             (:type (event->map (ResponseStreamEvent/ofReasoningSummaryPartAdded
                                 (-> (com.openai.models.responses.ResponseReasoningSummaryPartAddedEvent/builder)
                                     (.itemId "rs_1") (.outputIndex 0) (.part summary-added-part) (.summaryIndex 0) (.sequenceNumber 53) (.build)))))))
      (is (= :reasoning-summary-part-done
             (:type (event->map (ResponseStreamEvent/ofReasoningSummaryPartDone
                                 (-> (com.openai.models.responses.ResponseReasoningSummaryPartDoneEvent/builder)
                                     (.itemId "rs_1") (.outputIndex 0) (.part summary-done-part) (.summaryIndex 0) (.sequenceNumber 54) (.build)))))))
      (is (= :output-text-annotation-added
             (:type (event->map (ResponseStreamEvent/ofOutputTextAnnotationAdded
                                 (-> (com.openai.models.responses.ResponseOutputTextAnnotationAddedEvent/builder)
                                     (.annotation (com.openai.core.JsonValue/from {"type" "url_citation"}))
                                     (.annotationIndex 0) (.contentIndex 0) (.itemId "msg_1")
                                     (.outputIndex 0) (.sequenceNumber 55) (.build))))))))))

(def throw-normalized! (deref (ns-resolve 'openai.core 'throw-normalized!)))

(defn- rate-limit-ex []
  (let [ctor (first (.getConstructors com.openai.errors.RateLimitException))
        err (-> (com.openai.models.ErrorObject/builder)
                (.code "rate_limited")
                (.message "too fast")
                (.param (java.util.Optional/empty))
                (.type "rate_limit_error")
                (.build))]
    (.newInstance ctor
                  (object-array [(.build (com.openai.core.http.Headers/builder))
                                 (com.openai.core.JsonField/of err)
                                 nil nil]))))

(deftest api-error-normalization
  (testing "service exceptions become ex-info with status, error-type, cause"
    (let [orig (rate-limit-ex)
          ex (try (throw-normalized! orig) (catch clojure.lang.ExceptionInfo e e))]
      (is (= :api-error (:openai/error (ex-data ex))))
      (is (= 429 (:status (ex-data ex))))
      (is (= :rate-limit (:error-type (ex-data ex))))
      (is (identical? orig (ex-cause ex)))))
  (testing "io exceptions become :io-error ex-info"
    (let [orig (com.openai.errors.OpenAIIoException. "boom")
          ex (try (throw-normalized! orig) (catch clojure.lang.ExceptionInfo e e))]
      (is (= :io-error (:openai/error (ex-data ex))))
      (is (identical? orig (ex-cause ex)))))
  (testing "other OpenAI exceptions pass through unchanged"
    (let [orig (com.openai.errors.OpenAIInvalidDataException. "bad" nil)
          ex (try (throw-normalized! orig) (catch Throwable e e))]
      (is (identical? orig ex)))))

(deftest translates-conversation-stream-options-moderation-verbosity
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :conversation "conv_123"
                   :stream-options {:include-obfuscation true}
                   :moderation {:model "omni-moderation-latest"}
                   :verbosity :low})]
    (is (= "conv_123" (-> p .conversation opt .asId)))
    (is (true? (-> p .streamOptions opt .includeObfuscation opt)))
    (is (= "omni-moderation-latest" (-> p .moderation opt .model)))
    (is (= "low" (-> p .text opt .verbosity opt str)))))

(def ->embedding-params #'openai/->embedding-params)
(def embedding-response->map #'openai/embedding-response->map)

(deftest translates-embedding-params
  (let [^com.openai.models.embeddings.EmbeddingCreateParams p
        (->embedding-params {:model "text-embedding-3-small"
                             :input "hello"
                             :dimensions 256
                             :user "u1"})]
    (is (= "text-embedding-3-small" (str (.model p))))
    (is (= "hello" (-> p .input .asString)))
    (is (= 256 (opt (.dimensions p))))
    (is (= "u1" (opt (.user p)))))
  (testing "vector input becomes array-of-strings"
    (let [^com.openai.models.embeddings.EmbeddingCreateParams p
          (->embedding-params {:model "text-embedding-3-small" :input ["a" "b"]})]
      (is (= ["a" "b"] (vec (-> p .input .asArrayOfStrings))))))
  (testing "missing keys throw"
    (is (= {:openai/error :missing-key :key :model}
           (ex-data-for #(->embedding-params {:input "x"}))))
    (is (= {:openai/error :missing-key :key :input}
           (ex-data-for #(->embedding-params {:model "m"}))))))

(deftest maps-embedding-response
  (let [emb (fn [idx vs]
              (-> (com.openai.models.embeddings.Embedding/builder)
                  (.index (int idx))
                  (.embedding ^java.util.List (mapv float vs))
                  (.build)))
        resp (-> (com.openai.models.embeddings.CreateEmbeddingResponse/builder)
                 (.model "text-embedding-3-small")
                 (.data [(emb 1 [0.3 0.4]) (emb 0 [0.1 0.2])])
                 (.usage (-> (com.openai.models.embeddings.CreateEmbeddingResponse$Usage/builder)
                             (.promptTokens 7)
                             (.totalTokens 7)
                             (.build)))
                 (.build))
        m (embedding-response->map resp)]
    (is (= "text-embedding-3-small" (:model m)))
    (is (= {:prompt-tokens 7 :total-tokens 7} (:usage m)))
    (testing "embeddings are ordered by index regardless of wire order"
      (is (= [[(float 0.1) (float 0.2)] [(float 0.3) (float 0.4)]]
             (:embeddings m))))))

(deftest client-accepts-azure-service-version
  (is (instance? com.openai.client.OpenAIClient
                 (openai/client {:api-key "sk-test"
                                 :base-url "https://example.openai.azure.com"
                                 :azure-service-version "2024-10-21"}))))

(deftest translates-compound-file-search-filters
  (testing "and/or of comparison filters"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :file-search
                                                  :vector-store-ids ["vs_1"]
                                                  :filters {:type :and
                                                            :filters [{:type :eq :key "kind" :value "docs"}
                                                                      {:type :gte :key "year" :value 2024}]}}]}))))
          fs (opt (.filters (.asFileSearch t)))
          cf (.asCompoundFilter fs)]
      (is (.isCompoundFilter fs))
      (is (= "and" (.asString (.type cf))))
      (let [[a b] (vec (.filters cf))]
        (is (= "kind" (.key (opt (.comparison a)))))
        (is (= "eq" (.asString (.type (opt (.comparison a))))))
        (is (= "year" (.key (opt (.comparison b)))))
        (is (= 2024.0 (opt (.number (.value (opt (.comparison b))))))))))
  (testing "nested compound filter"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :file-search
                                                  :vector-store-ids ["vs_1"]
                                                  :filters {:type :or
                                                            :filters [{:type :eq :key "kind" :value "docs"}
                                                                      {:type :and
                                                                       :filters [{:type :eq :key "team" :value "core"}
                                                                                 {:type :ne :key "draft" :value true}]}]}}]}))))
          cf (.asCompoundFilter (opt (.filters (.asFileSearch t))))
          [_ nested] (vec (.filters cf))
          j (opt (.jsonValue nested))
          nm (.convert j java.util.Map)]
      (is (= "or" (.asString (.type cf))))
      (is (some? nm))
      (is (= "and" (get nm "type")))
      (is (= 2 (count (get nm "filters"))))
      (is (= {"type" "eq" "key" "team" "value" "core"}
             (into {} (first (get nm "filters")))))))
  (testing "plain comparison map is still a comparison filter"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :file-search
                                                  :vector-store-ids ["vs_1"]
                                                  :filters {:type :eq :key "kind" :value "docs"}}]}))))
          fs (opt (.filters (.asFileSearch t)))]
      (is (.isComparisonFilter fs)))))

(def ->batch-create-params #'openai/->batch-create-params)
(def batch->map #'openai/batch->map)
(def ->batch-list-params #'openai/->batch-list-params)

(deftest translates-batch-create-params
  (let [^com.openai.models.batches.BatchCreateParams p
        (->batch-create-params {:input-file-id "file_1"
                                :endpoint "/v1/responses"
                                :completion-window "24h"
                                :metadata {:job "nightly"}
                                :output-expires-after {:seconds 3600}})]
    (is (= "file_1" (.inputFileId p)))
    (is (= "/v1/responses" (.asString (.endpoint p))))
    (is (= "24h" (.asString (.completionWindow p))))
    (is (= "nightly" (-> p .metadata opt ._additionalProperties (get "job") .asStringOrThrow)))
    (is (= 3600 (-> p .outputExpiresAfter opt .seconds))))
  (testing "completion window defaults to 24h"
    (let [^com.openai.models.batches.BatchCreateParams p
          (->batch-create-params {:input-file-id "file_1" :endpoint "/v1/responses"})]
      (is (= "24h" (.asString (.completionWindow p))))))
  (testing "missing keys throw"
    (is (= {:openai/error :missing-key :key :input-file-id}
           (ex-data-for #(->batch-create-params {:endpoint "/v1/responses"}))))
    (is (= {:openai/error :missing-key :key :endpoint}
           (ex-data-for #(->batch-create-params {:input-file-id "file_1"}))))))

(deftest maps-batch-to-clojure
  (let [batch (-> (com.openai.models.batches.Batch/builder)
                  (.id "batch_1")
                  (.completionWindow "24h")
                  (.createdAt 123)
                  (.endpoint "/v1/responses")
                  (.inputFileId "file_in")
                  (.status com.openai.models.batches.Batch$Status/COMPLETED)
                  (.outputFileId "file_out")
                  (.errorFileId "file_err")
                  (.requestCounts (-> (com.openai.models.batches.BatchRequestCounts/builder)
                                      (.completed 9) (.failed 1) (.total 10)
                                      (.build)))
                  (.build))
        m (batch->map batch)]
    (is (= {:id "batch_1"
            :status :completed
            :endpoint "/v1/responses"
            :input-file-id "file_in"
            :completion-window "24h"
            :created-at 123
            :output-file-id "file_out"
            :error-file-id "file_err"
            :request-counts {:completed 9 :failed 1 :total 10}}
           m)))
  (testing "optional fields are absent when unset"
    (let [m (batch->map (-> (com.openai.models.batches.Batch/builder)
                            (.id "batch_2")
                            (.completionWindow "24h")
                            (.createdAt 124)
                            (.endpoint "/v1/embeddings")
                            (.inputFileId "file_in2")
                            (.status com.openai.models.batches.Batch$Status/IN_PROGRESS)
                            (.build)))]
      (is (= :in-progress (:status m)))
      (is (not (contains? m :output-file-id)))
      (is (not (contains? m :request-counts))))))

(deftest translates-batch-list-params
  (let [^com.openai.models.batches.BatchListParams p
        (->batch-list-params {:after "batch_0" :limit 5})]
    (is (= "batch_0" (opt (.after p))))
    (is (= 5 (opt (.limit p))))))

(def ->file-create-params #'openai/->file-create-params)
(def file->map #'openai/file->map)
(def ->file-list-params #'openai/->file-list-params)

(deftest translates-file-create-params
  (let [tmp (java.io.File/createTempFile "openai-clj" ".jsonl")]
    (spit tmp "{\"custom_id\":\"1\"}\n")
    (try
      (let [^com.openai.models.files.FileCreateParams p
            (->file-create-params {:file (.toPath tmp) :purpose :batch})]
        (is (= "batch" (.asString (.purpose p))))
        (is (some? (.file p))))
      (testing "string path, byte array, and input stream are accepted"
        (let [^com.openai.models.files.FileCreateParams p1
              (->file-create-params {:file (.getPath tmp) :purpose :batch})
              ^com.openai.models.files.FileCreateParams p2
              (->file-create-params {:file (.getBytes "data") :purpose :batch :filename "d.jsonl"})
              ^com.openai.models.files.FileCreateParams p3
              (->file-create-params {:file (java.io.ByteArrayInputStream. (.getBytes "data"))
                                     :purpose :batch :filename "d.jsonl"})]
          (is (some? (.file p1)))
          (is (= "d.jsonl" (-> p2 ._file .filename opt)))
          (is (= "d.jsonl" (-> p3 ._file .filename opt)))))
      (testing "expires-after"
        (let [^com.openai.models.files.FileCreateParams p
              (->file-create-params {:file (.toPath tmp) :purpose :batch
                                     :expires-after {:seconds 7200}})]
          (is (= 7200 (-> p .expiresAfter opt .seconds)))))
      (testing "missing keys throw"
        (is (= {:openai/error :missing-key :key :file}
               (ex-data-for #(->file-create-params {:purpose :batch}))))
        (is (= {:openai/error :missing-key :key :purpose}
               (ex-data-for #(->file-create-params {:file (.toPath tmp)})))))
      (finally (.delete tmp)))))

(deftest maps-file-object-to-clojure
  (let [f (-> (com.openai.models.files.FileObject/builder)
              (.id "file_1")
              (.bytes 10)
              (.createdAt 123)
              (.filename "a.jsonl")
              (.purpose com.openai.models.files.FileObject$Purpose/BATCH)
              (.status com.openai.models.files.FileObject$Status/PROCESSED)
              (.expiresAt 999)
              (.build))
        m (file->map f)]
    (is (= {:id "file_1"
            :bytes 10
            :created-at 123
            :filename "a.jsonl"
            :purpose :batch
            :status :processed
            :expires-at 999}
           m)))
  (testing "expires-at absent when unset"
    (is (not (contains? (file->map (-> (com.openai.models.files.FileObject/builder)
                                       (.id "f") (.bytes 1) (.createdAt 1)
                                       (.filename "x")
                                       (.purpose com.openai.models.files.FileObject$Purpose/ASSISTANTS)
                                       (.status com.openai.models.files.FileObject$Status/UPLOADED)
                                       (.build)))
                        :expires-at)))))

(deftest translates-file-list-params
  (let [^com.openai.models.files.FileListParams p
        (->file-list-params {:purpose "batch" :order :desc :after "file_0" :limit 3})]
    (is (= "batch" (opt (.purpose p))))
    (is (= "desc" (.asString (opt (.order p)))))
    (is (= "file_0" (opt (.after p))))
    (is (= 3 (opt (.limit p))))))

(deftest translates-chat-message-roles-and-content
  (let [p (chat-params {:model "gpt-4o-mini"
                        :messages [{:role :system :content "sys"}
                                   {:role :developer :content "dev"}
                                   {:role :user :content "hi"}
                                   {:role :assistant :content "there"}
                                   {:role :tool :tool-call-id "call_1" :content "ok"}]})
        messages (.messages p)]
    (is (= "gpt-4o-mini" (.asString (.model p))))
    (is (.isSystem ^ChatCompletionMessageParam (first messages)))
    (is (.isDeveloper ^ChatCompletionMessageParam (second messages)))
    (is (.isUser ^ChatCompletionMessageParam (nth messages 2)))
    (is (.isAssistant ^ChatCompletionMessageParam (nth messages 3)))
    (is (.isTool ^ChatCompletionMessageParam (nth messages 4)))
    (is (= "sys" (-> ^ChatCompletionMessageParam (first messages) .asSystem .content .asText)))
    (is (= "dev" (-> ^ChatCompletionMessageParam (second messages) .asDeveloper .content .asText)))
    (is (= "hi" (-> ^ChatCompletionMessageParam (nth messages 2) .asUser .content .asText)))
    (is (= "there" (-> ^ChatCompletionMessageParam (nth messages 3) .asAssistant .content opt .asText)))
    (is (= "call_1" (-> ^ChatCompletionMessageParam (nth messages 4) .asTool .toolCallId)))))

(deftest translates-chat-user-content-parts
  (let [parts (-> (chat-params {:model "gpt-4o-mini"
                                :messages [{:role :user
                                            :content [{:type :text :text "look"}
                                                      {:type :image
                                                       :image-url "https://example.test/image.png"
                                                       :detail :high}
                                                      {:type :input-audio
                                                       :data "AAAA"
                                                       :format :wav}]}]})
                  .messages
                  first
                  .asUser
                  .content
                  .asArrayOfContentParts)
        text-part (.asText ^ChatCompletionContentPart (first parts))
        image-part (.asImageUrl ^ChatCompletionContentPart (second parts))
        audio-part (.asInputAudio ^ChatCompletionContentPart (nth parts 2))]
    (is (= "look" (.text text-part)))
    (is (= "https://example.test/image.png" (-> image-part .imageUrl .url)))
    (is (= "high" (-> image-part .imageUrl .detail opt .asString)))
    (is (= "AAAA" (-> audio-part .inputAudio .data)))
    (is (= "wav" (-> audio-part .inputAudio .format .asString)))))

(deftest translates-chat-scalar-options
  (let [p (chat-params {:model "gpt-4o-mini"
                        :messages [{:role :user :content "hi"}]
                        :temperature 0.1
                        :top-p 0.9
                        :max-tokens 64
                        :max-completion-tokens 32
                        :n 2
                        :stop ["END" "DONE"]
                        :presence-penalty 0.2
                        :frequency-penalty 0.3
                        :logit-bias {"42" -100}
                        :seed 123
                        :user "user_1"
                        :metadata {:app "tests"}
                        :store false
                        :service-tier :priority
                        :parallel-tool-calls true
                        :logprobs true
                        :top-logprobs 2
                        :reasoning-effort :low
                        :stream-options {:include-usage true}})]
    (is (= 0.1 (opt (.temperature p))))
    (is (= 0.9 (opt (.topP p))))
    (is (= 64 (opt (.maxTokens p))))
    (is (= 32 (opt (.maxCompletionTokens p))))
    (is (= 2 (opt (.n p))))
    (is (= ["END" "DONE"] (.asStrings (opt (.stop p)))))
    (is (= 0.2 (opt (.presencePenalty p))))
    (is (= 0.3 (opt (.frequencyPenalty p))))
    (is (= -100 (.convert (get (._additionalProperties (opt (.logitBias p))) "42") Integer)))
    (is (= 123 (opt (.seed p))))
    (is (= "user_1" (opt (.user p))))
    (is (= "tests" (.asStringOrThrow (get (._additionalProperties (opt (.metadata p))) "app"))))
    (is (false? (opt (.store p))))
    (is (= "priority" (.asString (opt (.serviceTier p)))))
    (is (true? (opt (.parallelToolCalls p))))
    (is (true? (opt (.logprobs p))))
    (is (= 2 (opt (.topLogprobs p))))
    (is (= "low" (.asString (opt (.reasoningEffort p)))))
    (is (true? (-> p .streamOptions opt .includeUsage opt)))))

(deftest translates-chat-tools-tool-choice-and-response-format
  (let [p (chat-params {:model "gpt-4o-mini"
                        :messages [{:role :user :content "weather"}]
                        :tools [{:type :function
                                 :name "get_weather"
                                 :description "Get weather"
                                 :strict true
                                 :parameters {:type "object"
                                              :properties {:location {:type "string"}}
                                              :required ["location"]}}]
                        :tool-choice {:type :function :name "get_weather"}
                        :response-format {:type :json-schema
                                          :json-schema {:name "answer"
                                                        :description "Answer"
                                                        :strict true
                                                        :schema {:type "object"
                                                                 :properties {:answer {:type "string"}}
                                                                 :required ["answer"]}}}})
        tool (first (opt (.tools p)))
        f (.function (.asFunction tool))
        params-props (._additionalProperties (opt (.parameters f)))
        tc (opt (.toolChoice p))
        rf (-> p .responseFormat opt .asJsonSchema .jsonSchema)
        schema-props (._additionalProperties (opt (.schema rf)))]
    (is (.isFunction tool))
    (is (= "get_weather" (.name f)))
    (is (= "Get weather" (opt (.description f))))
    (is (true? (opt (.strict f))))
    (is (= "object" (.asStringOrThrow (get params-props "type"))))
    (is (.isNamedToolChoice tc))
    (is (= "get_weather" (-> tc .asNamedToolChoice .function .name)))
    (is (= "answer" (.name rf)))
    (is (= "Answer" (opt (.description rf))))
    (is (true? (opt (.strict rf))))
    (is (= "object" (.asStringOrThrow (get schema-props "type"))))))

(deftest translates-chat-required-keys-and-unknown-keys
  (is (= {:openai/error :missing-key :key :model}
         (ex-data-for #(chat-params {:messages [{:role :user :content "hi"}]}))))
  (is (= {:openai/error :missing-key :key :messages}
         (ex-data-for #(chat-params {:model "gpt-4o-mini"}))))
  (let [p (chat-params {:model "gpt-4o-mini"
                        :messages [{:role :user :content "hi"}]
                        :unknown "ignored"})]
    (is (= "gpt-4o-mini" (.asString (.model p))))
    (is (= 1 (count (.messages p))))))

(defn- chat-tool-call [args]
  (ChatCompletionMessageToolCall/ofFunction
   (-> (ChatCompletionMessageFunctionToolCall/builder)
       (.id "call_1")
       (.function (-> (ChatCompletionMessageFunctionToolCall$Function/builder)
                      (.name "get_weather")
                      (.arguments args)
                      (.build)))
       (.build))))

(defn- chat-completion [args]
  (-> (ChatCompletion/builder)
      (.id "chatcmpl_1")
      (.model "gpt-4o-mini")
      (.created 1790000000)
      (.serviceTier ChatCompletion$ServiceTier/DEFAULT)
      (.usage (-> (CompletionUsage/builder)
                  (.promptTokens 10)
                  (.completionTokens 20)
                  (.totalTokens 30)
                  (.build)))
      (.choices [(-> (ChatCompletion$Choice/builder)
                     (.index 0)
                     (.finishReason ChatCompletion$Choice$FinishReason/TOOL_CALLS)
                     (.logprobs (java.util.Optional/empty))
                     (.message (-> (ChatCompletionMessage/builder)
                                   (.role (com.openai.core.JsonValue/from "assistant"))
                                   (.content "Use get_weather.")
                                   (.refusal "no")
                                   (.toolCalls [(chat-tool-call args)])
                                   (.build)))
                     (.build))])
      (.build)))

(deftest maps-chat-completion-to-clojure
  (let [m (chat-completion->map (chat-completion "{\"location\":\"Denver\"}"))]
    (is (= {:id "chatcmpl_1"
            :model "gpt-4o-mini"
            :created 1790000000
            :choices [{:index 0
                       :finish-reason :tool-calls
                       :message {:role :assistant
                                 :content "Use get_weather."
                                 :tool-calls [{:id "call_1"
                                               :type :function
                                               :function {:name "get_weather"
                                                          :arguments {:location "Denver"}}}]
                                 :refusal "no"}}]
            :usage {:prompt-tokens 10
                    :completion-tokens 20
                    :total-tokens 30}
            :text "Use get_weather."
            :service-tier :default}
           m))))

(deftest chat-completion-map-keeps-garbage-arguments-raw
  (let [m (chat-completion->map (chat-completion "{not json}"))]
    (is (= "{not json}" (-> m :choices first :message :tool-calls first :function :arguments)))))

(deftest maps-chat-stream-chunk-to-clojure
  (let [chunk (-> (ChatCompletionChunk/builder)
                  (.id "chunk_1")
                  (.model "gpt-4o-mini")
                  (.created 1790000001)
                  (.choices [(-> (ChatCompletionChunk$Choice/builder)
                                 (.index 0)
                                 (.finishReason ChatCompletionChunk$Choice$FinishReason/STOP)
                                 (.delta (-> (ChatCompletionChunk$Choice$Delta/builder)
                                             (.role ChatCompletionChunk$Choice$Delta$Role/ASSISTANT)
                                             (.content "Hel")
                                             (.toolCalls [(-> (ChatCompletionChunk$Choice$Delta$ToolCall/builder)
                                                              (.index 0)
                                                              (.id "call_1")
                                                              (.type ChatCompletionChunk$Choice$Delta$ToolCall$Type/FUNCTION)
                                                              (.function (-> (ChatCompletionChunk$Choice$Delta$ToolCall$Function/builder)
                                                                             (.name "get_weather")
                                                                             (.arguments "{\"")
                                                                             (.build)))
                                                              (.build))])
                                             (.build)))
                                 (.build))])
                  (.build))]
    (is (= {:type :chunk
            :choices [{:index 0
                       :finish-reason :stop
                       :delta {:role :assistant
                               :content "Hel"
                               :tool-calls [{:index 0
                                             :id "call_1"
                                             :type :function
                                             :function {:name "get_weather"
                                                        :arguments "{\""}}]}}]}
           (chat-chunk->map chunk)))))

(deftest maps-chat-stream-usage-only-chunk
  (let [chunk (-> (ChatCompletionChunk/builder)
                  (.id "chunk_2")
                  (.model "gpt-4o-mini")
                  (.created 1790000002)
                  (.choices [])
                  (.usage (-> (CompletionUsage/builder)
                              (.promptTokens 1)
                              (.completionTokens 2)
                              (.totalTokens 3)
                              (.build)))
                  (.build))]
    (is (= {:type :chunk
            :choices []
            :usage {:prompt-tokens 1
                    :completion-tokens 2
                    :total-tokens 3}}
           (chat-chunk->map chunk)))))

(deftest builds-stored-resource-params
  (let [model-p (model-delete-params "ft:model")
        update-p (chat-completion-update-params "chatcmpl_1" {:metadata {:team "sdk"}})
        list-p (chat-completion-list-params {:model "gpt-4o-mini"
                                             :metadata {:team "sdk"}
                                             :after "chatcmpl_0"
                                             :limit 25
                                             :order :asc})
        messages-p (chat-completion-message-list-params "chatcmpl_1"
                                                        {:after "msg_0" :limit 10 :order :desc})]
    (is (= "ft:model" (opt (.model model-p))))
    (is (= "chatcmpl_1" (opt (.completionId update-p))))
    (is (= "sdk" (-> update-p .metadata opt ._additionalProperties (get "team") .asStringOrThrow)))
    (is (= "gpt-4o-mini" (opt (.model list-p))))
    (is (= ["sdk"] (-> list-p .metadata opt ._additionalProperties (.values "team"))))
    (is (= "chatcmpl_0" (opt (.after list-p))))
    (is (= 25 (opt (.limit list-p))))
    (is (= "asc" (-> list-p .order opt .asString)))
    (is (= "chatcmpl_1" (opt (.completionId messages-p))))
    (is (= "msg_0" (opt (.after messages-p))))
    (is (= 10 (opt (.limit messages-p))))
    (is (= "desc" (-> messages-p .order opt .asString)))))

(deftest maps-stored-resource-results
  (is (= {:id "ft:model" :deleted true}
         (deleted-model->map (-> (com.openai.models.models.ModelDeleted/builder)
                                 (.id "ft:model")
                                 (.deleted true)
                                 (.object_ "model")
                                 (.build)))))
  (is (= {:id "chatcmpl_1" :deleted false}
         (deleted-chat-completion->map
          (-> (com.openai.models.chat.completions.ChatCompletionDeleted/builder)
              (.id "chatcmpl_1")
              (.deleted false)
              (.object_ (com.openai.core.JsonValue/from "chat.completion.deleted"))
              (.build)))))
  (is (= {:id "msg_1"
          :role :assistant
          :content "Calling a tool"
          :refusal "no"
          :tool-calls [{:id "call_1"
                        :type :function
                        :function {:name "get_weather"
                                   :arguments {:location "Denver"}}}]}
         (stored-chat-message->map
          (-> (ChatCompletionStoreMessage/builder)
              (.id "msg_1")
              (.role (com.openai.core.JsonValue/from "assistant"))
              (.content "Calling a tool")
              (.refusal "no")
              (.toolCalls [(chat-tool-call "{\"location\":\"Denver\"}")])
              (.build))))))
