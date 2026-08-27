(ns openai.impl
  "Shared helpers for OpenAI SDK wrapper namespaces."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [jsonista.core :as json])
  (:import (com.openai.core AutoPager JsonValue Page)
           (com.openai.errors BadRequestException
                              InternalServerException
                              NotFoundException
                              OpenAIIoException
                              OpenAIServiceException
                              PermissionDeniedException
                              RateLimitException
                              UnauthorizedException
                              UnexpectedStatusCodeException
                              UnprocessableEntityException)))

(set! *warn-on-reflection* true)

(defn service-error-type [e]
  (condp instance? e
    BadRequestException :bad-request
    UnauthorizedException :unauthorized
    PermissionDeniedException :permission-denied
    NotFoundException :not-found
    UnprocessableEntityException :unprocessable-entity
    RateLimitException :rate-limit
    InternalServerException :internal-server
    UnexpectedStatusCodeException :unexpected-status
    :api-error))

(defn throw-normalized!
  "Rethrow an SDK exception. Service and I/O errors become ex-info keyed
  `:openai/error` with the original as the cause. Other errors propagate unchanged."
  [^Throwable e]
  (cond
    (instance? OpenAIServiceException e)
    (throw (ex-info (or (.getMessage e) "OpenAI API error")
                    {:openai/error :api-error
                     :status (.statusCode ^OpenAIServiceException e)
                     :error-type (service-error-type e)}
                    e))
    (instance? OpenAIIoException e)
    (throw (ex-info (or (.getMessage e) "OpenAI I/O error")
                    {:openai/error :io-error}
                    e))
    :else (throw e)))

(defmacro with-api-errors [& body]
  `(try ~@body
        (catch com.openai.errors.OpenAIException e#
          (openai.impl/throw-normalized! e#))))

(defn missing-key! [k]
  (throw (ex-info (str "Missing required key " k)
                  {:openai/error :missing-key :key k})))

(defn enum-name [k]
  (str/replace (name k) "-" "_"))

(defn ->keyword [s]
  (-> s str str/lower-case (str/replace "_" "-") keyword))

(defn opt-get [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(def json-mapper (json/object-mapper {:decode-key-fn true}))

(defn parse-arguments [^String s]
  (try
    (json/read-value s json-mapper)
    (catch Exception _
      s)))

(defn parse-json [^String s]
  (json/read-value s json-mapper))

(defn- schema-value [schema k]
  (if (contains? schema k)
    (get schema k)
    (get schema (name k))))

(defn- data-type [x]
  (cond
    (nil? x) "null"
    (map? x) "object"
    (sequential? x) "array"
    (string? x) "string"
    (integer? x) "integer"
    (number? x) "number"
    (instance? Boolean x) "boolean"
    :else (.getSimpleName (class x))))

(defn- type-valid? [expected x]
  (case expected
    "null" (nil? x)
    "object" (map? x)
    "array" (sequential? x)
    "string" (string? x)
    "integer" (integer? x)
    "number" (number? x)
    "boolean" (instance? Boolean x)
    true))

(declare validate-json-schema)

(defn- validate-schema* [schema data path]
  (let [expected (schema-value schema :type)
        expected-types (if (sequential? expected) expected [expected])
        type-error (when (and expected
                              (not-any? #(type-valid? % data) expected-types))
                     {:path path :error :type
                      :expected expected
                      :actual (data-type data)})]
    (if type-error
      [type-error]
      (let [enum-values (schema-value schema :enum)
            const-present? (or (contains? schema :const) (contains? schema "const"))
            const-value (schema-value schema :const)
            value-errors (cond-> []
                           (and enum-values (not (some #(= data %) enum-values)))
                           (conj {:path path :error :enum :allowed (vec enum-values)})
                           (and const-present? (not= const-value data))
                           (conj {:path path :error :const :expected const-value}))
            object-errors
            (when (map? data)
              (let [properties (or (schema-value schema :properties) {})
                    required (or (schema-value schema :required) [])
                    additional (schema-value schema :additionalProperties)
                    prop-key #(if (keyword? %) % (keyword (str %)))
                    required-errors
                    (keep (fn [k]
                            (let [k' (prop-key k)]
                              (when-not (contains? data k')
                                {:path path :error :required :key k'})))
                          required)
                    property-errors
                    (mapcat (fn [[k child-schema]]
                              (let [k' (prop-key k)]
                                (when (contains? data k')
                                  (validate-schema* child-schema (get data k') (conj path k')))))
                            properties)
                    known (set (map (comp prop-key key) properties))
                    additional-errors
                    (when (false? additional)
                      (for [k (sort-by name (remove known (keys data)))]
                        {:path path :error :additional-property :key k}))]
                (concat required-errors property-errors additional-errors)))
            array-errors
            (when (sequential? data)
              (when-let [item-schema (schema-value schema :items)]
                (mapcat (fn [[i x]] (validate-schema* item-schema x (conj path i)))
                        (map-indexed vector data))))]
        (vec (concat value-errors object-errors array-errors))))))

(defn validate-json-schema
  "Return JSON Schema conformance errors for parsed Clojure data. Supports
  structural keywords from Responses strict schemas."
  [schema data]
  (validate-schema* schema data []))

(defn encode-output [x]
  (if (string? x)
    x
    (json/write-value-as-string x)))

(defn ->json-value-properties [m]
  (into {}
        (map (fn [[k v]] [k (JsonValue/from v)]))
        (walk/stringify-keys m)))

(defn ->json-schema-properties [m]
  (into {}
        (map (fn [[k v]] [k (JsonValue/from v)]))
        (walk/stringify-keys m)))

(defn json-value->clj [^JsonValue value]
  (walk/keywordize-keys (.convert value Object)))

(defn sdk-input-object [m ^Class target]
  (let [m (cond-> m
            (and (map? m)
                 (#{:function-call :mcp-call :mcp-approval-request} (:type m))
                 (map? (:arguments m)))
            (update :arguments encode-output))]
    (.convert (JsonValue/from
             (walk/postwalk
              (fn [x]
                (cond
                  (keyword? x) (str/replace (name x) "-" "_")
                  (map? x) (into {}
                                 (map (fn [[k v]]
                                        [(str/replace (if (keyword? k) (name k) (str k))
                                                      "-" "_")
                                         v])
                                      x))
                  :else x))
              m))
          target)))

(defn sdk-object->clj [value]
  (walk/postwalk
   (fn [x]
     (if (map? x)
       (into {}
             (keep (fn [[k v]]
                     (let [k' (->keyword (if (keyword? k) (name k) k))]
                       (when-not (= :valid k') [k' v]))))
             x)
       x))
   (json/read-value (json/write-value-as-string value) json-mapper)))

(defn preserve-raw
  "Attach the complete parsed SDK object under `:openai/raw` when requested."
  [curated value {:keys [lossless?]}]
  (if lossless?
    (assoc curated :openai/raw (sdk-object->clj value))
    curated))

(defn output-text [items]
  (apply str
         (for [item items
               :when (= :message (:type item))
               content (:content item)
               :when (#{:text :output-text} (:type content))]
           (:text content))))

(defn all-pages
  "Realize each element from all pages of an SDK *ListPage with its autoPager."
  [page]
  (vec (AutoPager. ^Page page nil)))

(defn lazy-pages
  "Return a lazy sequence of elements from an SDK *ListPage.

  `:max-items` limits elements consumed and `:max-pages` limits pages
  traversed. Both bounds are optional and are excluded from SDK request opts."
  [^Page page {:keys [max-items max-pages]}]
  (when (and max-items (neg? (long max-items)))
    (throw (ex-info ":max-items must be non-negative" {:max-items max-items})))
  (when (and max-pages (neg? (long max-pages)))
    (throw (ex-info ":max-pages must be non-negative" {:max-pages max-pages})))
  (letfn [(items [item-seq ^Page page pages-left]
            (lazy-seq
              (if-let [s (seq item-seq)]
                (cons (first s) (items (next s) page pages-left))
                (when (and (.hasNextPage page)
                           (or (nil? pages-left) (> pages-left 1)))
                  (let [next-page (.nextPage page)]
                    (pages next-page (when pages-left (dec pages-left))))))))
          (pages [^Page page pages-left]
            (lazy-seq
              (when (and page (or (nil? pages-left) (pos? pages-left)))
                (items (.items page) page pages-left))))]
    (let [result (pages page max-pages)]
      (if max-items
        (take (long max-items) result)
        result))))
