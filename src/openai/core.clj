(ns openai.core
  "Core client plus Responses, Chat Completions, embeddings, files, batches,
  and models. Additional stable APIs live in sibling `openai.*` namespaces."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.client.okhttp OpenAIOkHttpClient
                                      OpenAIOkHttpClient$Builder)
           (com.openai.core JsonValue MultipartField LogLevel)
           (com.openai.core.http StreamResponse)
           (com.openai.auth WorkloadIdentity)
           (java.net InetSocketAddress Proxy Proxy$Type)
           (java.time Duration)
           (java.util.concurrent Executor ExecutorService)
           (com.openai.models ComparisonFilter
                              ComparisonFilter$Builder
                              ComparisonFilter$Type
                              CompoundFilter
                              CompoundFilter$Builder
                              CompoundFilter$Type
                              FunctionDefinition
                              FunctionDefinition$Builder
                              FunctionParameters
                              FunctionParameters$Builder
                              Reasoning
                              Reasoning$Builder
                              Reasoning$Mode
                              ReasoningEffort
                              ResponseFormatJsonObject
                              ResponseFormatJsonObject$Builder
                              ResponseFormatJsonSchema
                              ResponseFormatJsonSchema$Builder
                              ResponseFormatJsonSchema$JsonSchema
                              ResponseFormatJsonSchema$JsonSchema$Builder
                              ResponseFormatJsonSchema$JsonSchema$Schema
                              ResponseFormatJsonSchema$JsonSchema$Schema$Builder
                              ResponsesModel)
           (com.openai.models.chat.completions ChatCompletion
                                               ChatCompletion$Choice
                                               ChatCompletion$Choice$FinishReason
                                               ChatCompletion$ServiceTier
                                               ChatCompletionAssistantMessageParam
                                               ChatCompletionAssistantMessageParam$Builder
                                               ChatCompletionChunk
                                               ChatCompletionChunk$Choice
                                               ChatCompletionChunk$Choice$Delta
                                               ChatCompletionChunk$Choice$Delta$Role
                                               ChatCompletionChunk$Choice$Delta$ToolCall
                                               ChatCompletionChunk$Choice$Delta$ToolCall$Function
                                               ChatCompletionChunk$Choice$Delta$ToolCall$Type
                                               ChatCompletionChunk$Choice$FinishReason
                                               ChatCompletionContentPart
                                               ChatCompletionContentPartImage
                                               ChatCompletionContentPartImage$Builder
                                               ChatCompletionContentPartImage$ImageUrl
                                               ChatCompletionContentPartImage$ImageUrl$Builder
                                               ChatCompletionContentPartImage$ImageUrl$Detail
                                               ChatCompletionContentPartInputAudio
                                               ChatCompletionContentPartInputAudio$Builder
                                               ChatCompletionContentPartInputAudio$InputAudio
                                               ChatCompletionContentPartInputAudio$InputAudio$Builder
                                               ChatCompletionContentPartInputAudio$InputAudio$Format
                                               ChatCompletionContentPartText
                                               ChatCompletionContentPartText$Builder
                                               ChatCompletionCreateParams
                                               ChatCompletionCreateParams$Builder
                                               ChatCompletionCreateParams$LogitBias
                                               ChatCompletionCreateParams$LogitBias$Builder
                                               ChatCompletionCreateParams$Metadata
                                               ChatCompletionCreateParams$Metadata$Builder
                                               ChatCompletionCreateParams$ResponseFormat
                                               ChatCompletionCreateParams$ServiceTier
                                               ChatCompletionDeleted
                                               ChatCompletionDeveloperMessageParam
                                               ChatCompletionDeveloperMessageParam$Builder
                                               ChatCompletionFunctionTool
                                               ChatCompletionFunctionTool$Builder
                                               ChatCompletionMessage
                                               ChatCompletionMessageFunctionToolCall
                                               ChatCompletionMessageFunctionToolCall$Builder
                                               ChatCompletionMessageFunctionToolCall$Function
                                               ChatCompletionMessageFunctionToolCall$Function$Builder
                                               ChatCompletionMessageParam
                                               ChatCompletionMessageToolCall
                                               ChatCompletionListPage
                                               ChatCompletionListParams
                                               ChatCompletionListParams$Builder
                                               ChatCompletionListParams$Metadata
                                               ChatCompletionListParams$Metadata$Builder
                                               ChatCompletionListParams$Order
                                               ChatCompletionNamedToolChoice
                                               ChatCompletionNamedToolChoice$Builder
                                               ChatCompletionNamedToolChoice$Function
                                               ChatCompletionNamedToolChoice$Function$Builder
                                               ChatCompletionStreamOptions
                                               ChatCompletionStreamOptions$Builder
                                               ChatCompletionSystemMessageParam
                                               ChatCompletionSystemMessageParam$Builder
                                               ChatCompletionTool
                                               ChatCompletionToolChoiceOption
                                               ChatCompletionToolChoiceOption$Auto
                                               ChatCompletionToolMessageParam
                                               ChatCompletionToolMessageParam$Builder
                                               ChatCompletionUpdateParams
                                               ChatCompletionUpdateParams$Builder
                                               ChatCompletionUpdateParams$Metadata
                                               ChatCompletionUpdateParams$Metadata$Builder
                                               ChatCompletionUserMessageParam
                                               ChatCompletionUserMessageParam$Builder
                                               ChatCompletionStoreMessage)
           (com.openai.models.chat.completions.messages MessageListPage
                                                        MessageListParams
                                                        MessageListParams$Builder
                                                        MessageListParams$Order)
           (com.openai.models.completions CompletionUsage)
           (com.openai.models.batches Batch
                                      BatchCreateParams
                                      BatchCreateParams$Builder
                                      BatchCreateParams$CompletionWindow
                                      BatchCreateParams$Endpoint
                                      BatchCreateParams$Metadata
                                      BatchCreateParams$Metadata$Builder
                                      BatchCreateParams$OutputExpiresAfter
                                      BatchCreateParams$OutputExpiresAfter$Builder
                                      BatchListPage
                                      BatchListParams
                                      BatchListParams$Builder
                                      BatchRequestCounts)
           (com.openai.models.files FileCreateParams
                                    FileCreateParams$Builder
                                    FileCreateParams$ExpiresAfter
                                    FileCreateParams$ExpiresAfter$Builder
                                    FileDeleted
                                    FileListPage
                                    FileListParams
                                    FileListParams$Builder
                                    FileListParams$Order
                                    FileObject
                                    FilePurpose)
           (com.openai.azure AzureOpenAIServiceVersion)
           (com.openai.models.embeddings CreateEmbeddingResponse
                                         CreateEmbeddingResponse$Usage
                                         Embedding
                                         EmbeddingCreateParams
                                         EmbeddingCreateParams$Builder
                                         EmbeddingCreateParams$Input)
           (com.openai.models.models Model
                                      ModelDeleteParams
                                      ModelDeleted
                                      ModelListPage)
           (com.openai.models.responses EasyInputMessage
                                         EasyInputMessage$Builder
                                         EasyInputMessage$Role
                                         FileSearchTool
                                         FileSearchTool$Builder
                                         FileSearchTool$Filters
                                         FileSearchTool$RankingOptions
                                         FileSearchTool$RankingOptions$Builder
                                         FileSearchTool$RankingOptions$Ranker
                                         FunctionTool
                                         FunctionTool$Builder
                                         FunctionTool$Parameters
                                         FunctionTool$Parameters$Builder
                                         Response
                                         Response$IncompleteDetails
                                         ResponseCreateParams
                                         ResponseCreateParams$Builder
                                         ResponseCreateParams$Input
                                         ResponseCreateParams$ContextManagement
                                         ResponseCreateParams$ContextManagement$Builder
                                         ResponseCreateParams$Metadata
                                         ResponseCreateParams$Metadata$Builder
                                         ResponseCreateParams$PromptCacheOptions
                                         ResponseCreateParams$PromptCacheRetention
                                         ResponseCreateParams$PromptCacheOptions$Mode
                                         ResponseCreateParams$PromptCacheOptions$Ttl
                                         ResponseCreateParams$ServiceTier
                                         ResponseCreateParams$ToolChoice
                                         ResponseCreateParams$Truncation
                                         ResponseCreateParams$StreamOptions
                                         ResponseCreateParams$StreamOptions$Builder
                                         ResponseCreateParams$Moderation
                                         ResponseCreateParams$Moderation$Builder
                                         ResponseCompletedEvent
                                         ResponseError
                                         ResponseErrorEvent
                                         ResponseFailedEvent
                                         ResponseFunctionCallArgumentsDeltaEvent
                                         ResponseFunctionCallArgumentsDoneEvent
                                         ResponseFunctionToolCall
                                         ResponseFunctionWebSearch
                                         ResponseIncompleteEvent
                                         ResponseIncludable
                                         ResponseFormatTextJsonSchemaConfig
                                         ResponseFormatTextJsonSchemaConfig$Builder
                                         ResponseFormatTextJsonSchemaConfig$Schema
                                         ResponseFormatTextJsonSchemaConfig$Schema$Builder
                                         ResponseInputContent
                                         ResponseInputFile
                                         ResponseInputFile$Builder
                                         ResponseInputImage
                                         ResponseInputImage$Builder
                                         ResponseInputImage$Detail
                                         ResponseInputMessageItem
                                         ResponseInputItem
                                         ResponseInputItem$FunctionCallOutput
                                         ResponseInputItem$FunctionCallOutput$Builder
                                         ResponseInputText
                                         ResponseInputText$Builder
                                         ResponseItem
                                         ResponseOutputItemAddedEvent
                                         ResponseOutputItemDoneEvent
                                         ResponseOutputItem
                                         ResponseOutputItem$ImageGenerationCall
                                         ResponseOutputItem$LocalShellCall
                                         ResponseOutputItem$McpApprovalRequest
                                         ResponseOutputItem$McpCall
                                         ResponseOutputItem$McpListTools
                                         ResponseOutputMessage
                                         ResponseOutputMessage$Content
                                         ResponseOutputRefusal
                                         ResponseOutputText
                                         ResponseOutputText$Annotation
                                         ResponseOutputText$Annotation$ContainerFileCitation
                                         ResponseOutputText$Annotation$FileCitation
                                         ResponseOutputText$Annotation$FilePath
                                         ResponseOutputText$Annotation$UrlCitation
                                         ResponseOutputText$Logprob
                                         ResponseOutputText$Logprob$TopLogprob
                                         ResponsePrompt
                                         ResponsePrompt$Builder
                                         ResponsePrompt$Variables
                                         ResponsePrompt$Variables$Builder
                                         ResponseReasoningItem
                                         ResponseReasoningItem$Status
                                         ResponseReasoningItem$Summary
                                         ResponseReasoningTextDeltaEvent
                                         ResponseReasoningTextDoneEvent
                                         ResponseRefusalDeltaEvent
                                         ResponseRefusalDoneEvent
                                         ResponseStatus
                                         ResponseStreamEvent
                                         ResponseTextConfig
                                         ResponseTextConfig$Builder
                                         ResponseTextConfig$Verbosity
                                         ResponseTextDeltaEvent
                                         ResponseTextDoneEvent
                                         ResponseUsage
                                         Tool
                                         Tool$CodeInterpreter
                                         Tool$CodeInterpreter$Builder
                                         Tool$CodeInterpreter$Container$CodeInterpreterToolAuto
                                         Tool$CodeInterpreter$Container$CodeInterpreterToolAuto$Builder
                                         Tool$ImageGeneration
                                         Tool$ImageGeneration$Action
                                         Tool$ImageGeneration$Background
                                         Tool$ImageGeneration$InputFidelity
                                         Tool$ImageGeneration$Moderation
                                         Tool$ImageGeneration$OutputFormat
                                         Tool$ImageGeneration$Quality
                                         Tool$Mcp
                                         Tool$Mcp$Builder
                                         Tool$Mcp$Headers
                                         Tool$Mcp$Headers$Builder
                                         Tool$Mcp$RequireApproval$McpToolApprovalSetting
                                         ToolChoiceFunction
                                         ToolChoiceFunction$Builder
                                         ToolChoiceOptions
                                         WebSearchTool
                                         WebSearchTool$Builder
                                         WebSearchTool$Filters
                                         WebSearchTool$Filters$Builder
                                         WebSearchTool$SearchContextSize
                                         WebSearchTool$UserLocation
                                         WebSearchTool$UserLocation$Builder
                                         WebSearchTool$Type)
           (com.openai.models.responses.inputitems InputItemListPage
                                                   InputItemListParams
                                                   InputItemListParams$Builder
                                                   InputItemListParams$Order)
           (com.openai.models.responses.inputtokens InputTokenCountParams
                                                      InputTokenCountParams$Builder
                                                      InputTokenCountParams$Truncation
                                                      InputTokenCountResponse)
           (com.openai.services.blocking BatchService
                                         ChatService
                                         EmbeddingService
                                         FileService
                                         ModelService
                                         ResponseService)
           (com.openai.services.blocking.chat ChatCompletionService)
           (com.openai.services.blocking.chat.completions MessageService)
           (com.openai.services.blocking.responses InputItemService
                                                    InputTokenService)))

(set! *warn-on-reflection* true)

(defn- invalid-client-option! [option message]
  (throw (ex-info (str option " " message)
                  {:openai/error :invalid-client-option
                   :option option})))

(defn- ->headers [headers]
  (when-not (map? headers)
    (invalid-client-option! :headers "must be a map"))
  (into {}
        (map (fn [[k value]]
               (let [values (if (string? value) [value] value)]
                 (when-not (and (sequential? values) (every? string? values))
                   (invalid-client-option! :headers
                                           "values must be strings or sequences of strings"))
                 [(str k) (vec values)])))
        headers))

(defn- ->proxy [proxy]
  (cond
    (instance? Proxy proxy) proxy
    (map? proxy)
    (let [{:keys [host port type]} proxy
          proxy-type (or type :http)]
      (when-not (string? host)
        (invalid-client-option! :proxy "host must be a string"))
      (when-not (and (integer? port) (<= 1 port 65535))
        (invalid-client-option! :proxy "port must be an integer from 1 to 65535"))
      (when-not (#{:http :socks} proxy-type)
        (invalid-client-option! :proxy "type must be :http or :socks"))
      (Proxy. (if (= :socks proxy-type) Proxy$Type/SOCKS Proxy$Type/HTTP)
               (InetSocketAddress/createUnresolved host (int port))))
    :else (invalid-client-option! :proxy "must be a java.net.Proxy or host/port map")))

(defn- ->log-level [level]
  (when-not (keyword? level)
    (invalid-client-option! :log-level "must be a keyword"))
  (try
    (LogLevel/valueOf (.toUpperCase ^String (name level)))
    (catch IllegalArgumentException _
      (invalid-client-option! :log-level "must be one of :off, :info, :error, or :debug"))))

(defn client
  "An OpenAI client. With no args, it reads credentials from the environment
  (`OPENAI_API_KEY`). Use explicit config keys to set client options:
  `:api-key`, `:organization`, `:project`, `:base-url`, `:timeout-ms`,
  `:max-retries`, `:admin-api-key`, `:headers`, `:proxy`, `:executor`,
  `:stream-handler-executor`, `:log-level`, `:workload-identity`, and
  `:azure-service-version` (an Azure OpenAI api-version string, used together
  with an Azure `:base-url`)."
  (^OpenAIClient [] (OpenAIOkHttpClient/fromEnv))
  (^OpenAIClient [{:keys [api-key organization project base-url timeout-ms max-retries webhook-secret
                          azure-service-version admin-api-key headers proxy executor
                          stream-handler-executor log-level workload-identity]}]
   (let [^OpenAIOkHttpClient$Builder b (OpenAIOkHttpClient/builder)]
     (when api-key (.apiKey b ^String api-key))
     (when admin-api-key
       (when-not (string? admin-api-key)
         (invalid-client-option! :admin-api-key "must be a string"))
       (.adminApiKey b ^String admin-api-key))
     (when organization (.organization b ^String organization))
     (when project (.project b ^String project))
     (when base-url (.baseUrl b ^String base-url))
     (when timeout-ms (.timeout b (Duration/ofMillis (long timeout-ms))))
     (when max-retries (.maxRetries b (int max-retries)))
     (when webhook-secret (.webhookSecret b ^String webhook-secret))
     (when headers (.headers b ^java.util.Map (->headers headers)))
     (when proxy (.proxy b ^Proxy (->proxy proxy)))
     (when executor
       (when-not (instance? ExecutorService executor)
         (invalid-client-option! :executor "must be an ExecutorService"))
       (.dispatcherExecutorService b ^ExecutorService executor))
     (when stream-handler-executor
       (when-not (instance? Executor stream-handler-executor)
         (invalid-client-option! :stream-handler-executor "must be an Executor"))
       (.streamHandlerExecutor b ^Executor stream-handler-executor))
     (when log-level (.logLevel b (->log-level log-level)))
     (when workload-identity
       (when-not (instance? WorkloadIdentity workload-identity)
         (invalid-client-option! :workload-identity "must be a WorkloadIdentity"))
       (.workloadIdentity b ^WorkloadIdentity workload-identity))
     (when azure-service-version
       (.azureServiceVersion b (AzureOpenAIServiceVersion/fromString ^String azure-service-version)))
     (.build b))))

#_:clj-kondo/ignore
(defn- throw-normalized! [^Throwable e]
  (impl/throw-normalized! e))

(defn- ->metadata ^ResponseCreateParams$Metadata [m]
  (let [^ResponseCreateParams$Metadata$Builder b (ResponseCreateParams$Metadata/builder)]
    (doseq [[k v] (walk/stringify-keys m)]
      (.putAdditionalProperty b ^String k (JsonValue/from (str v))))
    (.build b)))

(defn- ->role ^EasyInputMessage$Role [role]
  (EasyInputMessage$Role/of (name role)))

(defn- ->function-call-output ^ResponseInputItem
  [{:keys [call-id output name namespace]}]
  (let [^ResponseInputItem$FunctionCallOutput$Builder b
        (ResponseInputItem$FunctionCallOutput/builder)]
    (when-not call-id (impl/missing-key! :call-id))
    (.callId b ^String call-id)
    (.output b ^String (impl/encode-output output))
    (when name (.name b ^String name))
    (when namespace (.namespace b ^String namespace))
    (ResponseInputItem/ofFunctionCallOutput (.build b))))

(defn- ->computer-call-output ^ResponseInputItem
  [{:keys [call-id output acknowledged-safety-checks status id]}]
  (when-not call-id (impl/missing-key! :call-id))
  (when-not output (impl/missing-key! :output))
  (let [b (com.openai.models.responses.ResponseInputItem$ComputerCallOutput/builder)
        screenshot (com.openai.models.responses.ResponseComputerToolCallOutputScreenshot/builder)]
    (.callId b ^String call-id)
    (when id (.id b ^String id))
    (when-let [file-id (:file-id output)] (.fileId screenshot ^String file-id))
    (when-let [image-url (:image-url output)] (.imageUrl screenshot ^String image-url))
    (.output b (.build screenshot))
    (doseq [{:keys [id code message]} acknowledged-safety-checks]
      (when-not id (impl/missing-key! :id))
      (let [check (com.openai.models.responses.ResponseInputItem$ComputerCallOutput$AcknowledgedSafetyCheck/builder)]
        (.id check ^String id)
        (when code (.code check ^String code))
        (when message (.message check ^String message))
        (.addAcknowledgedSafetyCheck b (.build check))))
    (when status
      (.status b (com.openai.models.responses.ResponseInputItem$ComputerCallOutput$Status/of
                  (impl/enum-name status))))
    (ResponseInputItem/ofComputerCallOutput (.build b))))

(defn- ->local-shell-call-output ^ResponseInputItem [{:keys [id output status]}]
  (when-not id (impl/missing-key! :id))
  (when-not (some? output) (impl/missing-key! :output))
  (let [b (com.openai.models.responses.ResponseInputItem$LocalShellCallOutput/builder)]
    (.id b ^String id)
    (.output b ^String (impl/encode-output output))
    (when status
      (.status b (com.openai.models.responses.ResponseInputItem$LocalShellCallOutput$Status/of
                  (impl/enum-name status))))
    (ResponseInputItem/ofLocalShellCallOutput (.build b))))

(defn- ->shell-output-content [{:keys [stdout stderr exit-code outcome]}]
  (let [b (com.openai.models.responses.ResponseFunctionShellCallOutputContent/builder)]
    (.stdout b ^String (str (or stdout "")))
    (.stderr b ^String (str (or stderr "")))
    (if (= :timeout outcome)
      (.outcomeTimeout b)
      (.exitOutcome b (long (or exit-code 0))))
    (.build b)))

(defn- ->shell-call-output ^ResponseInputItem
  [{:keys [call-id output id status max-output-length]}]
  (when-not call-id (impl/missing-key! :call-id))
  (when-not output (impl/missing-key! :output))
  (let [b (com.openai.models.responses.ResponseInputItem$ShellCallOutput/builder)]
    (.callId b ^String call-id)
    (.output b ^java.util.List (mapv ->shell-output-content output))
    (when id (.id b ^String id))
    (when max-output-length (.maxOutputLength b (long max-output-length)))
    (when status
      (.status b (com.openai.models.responses.ResponseInputItem$ShellCallOutput$Status/of
                  (impl/enum-name status))))
    (ResponseInputItem/ofShellCallOutput (.build b))))

(defn- ->custom-tool-call-output ^ResponseInputItem [{:keys [call-id output id]}]
  (when-not call-id (impl/missing-key! :call-id))
  (when-not (some? output) (impl/missing-key! :output))
  (let [b (com.openai.models.responses.ResponseCustomToolCallOutput/builder)]
    (.callId b ^String call-id)
    (.output b ^String (impl/encode-output output))
    (when id (.id b ^String id))
    (ResponseInputItem/ofCustomToolCallOutput (.build b))))

(declare ->tool)

(defn- ->tool-search-output ^ResponseInputItem [{:keys [tools call-id id execution status]}]
  (when-not tools (impl/missing-key! :tools))
  (let [b (com.openai.models.responses.ResponseToolSearchOutputItemParam/builder)]
    (.tools b ^java.util.List (mapv ->tool tools))
    (when call-id (.callId b ^String call-id))
    (when id (.id b ^String id))
    (when execution
      (.execution b (com.openai.models.responses.ResponseToolSearchOutputItemParam$Execution/of
                     (impl/enum-name execution))))
    (when status
      (.status b (com.openai.models.responses.ResponseToolSearchOutputItemParam$Status/of
                  (impl/enum-name status))))
    (ResponseInputItem/ofToolSearchOutput (.build b))))

(defn- ->mcp-approval-response ^ResponseInputItem
  [{:keys [approval-request-id approve reason id]}]
  (when-not approval-request-id (impl/missing-key! :approval-request-id))
  (when-not (some? approve) (impl/missing-key! :approve))
  (let [b (com.openai.models.responses.ResponseInputItem$McpApprovalResponse/builder)]
    (.approvalRequestId b ^String approval-request-id)
    (.approve b (boolean approve))
    (when reason (.reason b ^String reason))
    (when id (.id b ^String id))
    (ResponseInputItem/ofMcpApprovalResponse (.build b))))

(defn- ->input-text ^ResponseInputContent [{:keys [text]}]
  (let [^ResponseInputText$Builder b (ResponseInputText/builder)]
    (when-not text (impl/missing-key! :text))
    (.text b ^String text)
    (ResponseInputContent/ofInputText (.build b))))

(defn- ->input-image ^ResponseInputContent [{:keys [image-url file-id detail]}]
  (let [^ResponseInputImage$Builder b (ResponseInputImage/builder)]
    (when image-url (.imageUrl b ^String image-url))
    (when file-id (.fileId b ^String file-id))
    (when detail (.detail b (ResponseInputImage$Detail/of (impl/enum-name detail))))
    (ResponseInputContent/ofInputImage (.build b))))

(defn- ->input-file ^ResponseInputContent [{:keys [file-id filename file-data]}]
  (let [^ResponseInputFile$Builder b (ResponseInputFile/builder)]
    (when file-id (.fileId b ^String file-id))
    (when filename (.filename b ^String filename))
    (when file-data (.fileData b ^String file-data))
    (ResponseInputContent/ofInputFile (.build b))))

(defn- ->input-content ^ResponseInputContent [{:keys [type] :as part}]
  (case (keyword type)
    :text (->input-text part)
    :image (->input-image part)
    :file (->input-file part)
    (throw (ex-info (str "Unknown content type " type)
                    {:openai/error :unknown-content-type :type type}))))

(defn- ->message-input-item ^ResponseInputItem [{:keys [role content]}]
  (when-not role (impl/missing-key! :role))
  (when-not content (impl/missing-key! :content))
  (let [b (com.openai.models.responses.ResponseInputItem$Message/builder)]
    (.role b (com.openai.models.responses.ResponseInputItem$Message$Role/of (name role)))
    (if (string? content)
      (.addInputTextContent b ^String content)
      (.content b ^java.util.List (mapv ->input-content content)))
    (ResponseInputItem/ofMessage (.build b))))

(defn response-input-item ^ResponseInputItem [{:keys [role content type] :as item}]
  (case (keyword type)
    :additional-tools (impl/sdk-input-object item ResponseInputItem)
    :apply-patch-call (impl/sdk-input-object item ResponseInputItem)
    :apply-patch-call-output (impl/sdk-input-object item ResponseInputItem)
    :code-interpreter-call (impl/sdk-input-object item ResponseInputItem)
    :compaction (impl/sdk-input-object item ResponseInputItem)
    :compaction-trigger (impl/sdk-input-object item ResponseInputItem)
    :computer-call (impl/sdk-input-object item ResponseInputItem)
    :function-call-output (->function-call-output item)
    :function-call (impl/sdk-input-object item ResponseInputItem)
    :file-search-call (impl/sdk-input-object item ResponseInputItem)
    :image-generation-call (impl/sdk-input-object item ResponseInputItem)
    :item-reference (impl/sdk-input-object item ResponseInputItem)
    :local-shell-call (impl/sdk-input-object item ResponseInputItem)
    :computer-call-output (->computer-call-output item)
    :local-shell-call-output (->local-shell-call-output item)
    :mcp-approval-request (impl/sdk-input-object item ResponseInputItem)
    :mcp-approval-response (->mcp-approval-response item)
    :mcp-call (impl/sdk-input-object item ResponseInputItem)
    :mcp-list-tools (impl/sdk-input-object item ResponseInputItem)
    :program (impl/sdk-input-object item ResponseInputItem)
    :program-output (impl/sdk-input-object item ResponseInputItem)
    :reasoning (impl/sdk-input-object item ResponseInputItem)
    :response-output-message (impl/sdk-input-object (assoc item :type :message) ResponseInputItem)
    :shell-call-output (->shell-call-output item)
    :custom-tool-call-output (->custom-tool-call-output item)
    :custom-tool-call (impl/sdk-input-object item ResponseInputItem)
    :shell-call (impl/sdk-input-object item ResponseInputItem)
    :tool-search-call (impl/sdk-input-object item ResponseInputItem)
    :tool-search-output (->tool-search-output item)
    :web-search-call (impl/sdk-input-object item ResponseInputItem)
    :easy-input-message (impl/sdk-input-object (assoc item :type :message) ResponseInputItem)
    :message (if (:id item)
               (impl/sdk-input-object item ResponseInputItem)
               (->message-input-item item))
    (let [^EasyInputMessage$Builder b (EasyInputMessage/builder)]
      (when-not role (impl/missing-key! :role))
      (when-not content (impl/missing-key! :content))
      (.role b (->role role))
      (if (string? content)
        (.content b ^String content)
        (.contentOfResponseInputMessageContentList
         b ^java.util.List (mapv ->input-content content)))
      (ResponseInputItem/ofEasyInputMessage (.build b)))))

(defn- ->input ^ResponseCreateParams$Input [input]
  (if (string? input)
    (ResponseCreateParams$Input/ofText input)
    (ResponseCreateParams$Input/ofResponse
     ^java.util.List (mapv response-input-item input))))

(defn- ->reasoning ^Reasoning [{:keys [effort mode]}]
  (let [^Reasoning$Builder b (Reasoning/builder)]
    (when effort (.effort b (ReasoningEffort/of (name effort))))
    (when mode (.mode b (Reasoning$Mode/of (name mode))))
    (.build b)))

(defn- ->prompt-cache-options ^ResponseCreateParams$PromptCacheOptions
  [{:keys [mode ttl]}]
  (let [b (ResponseCreateParams$PromptCacheOptions/builder)]
    (when mode (.mode b (ResponseCreateParams$PromptCacheOptions$Mode/of (name mode))))
    (when ttl (.ttl b (ResponseCreateParams$PromptCacheOptions$Ttl/of (name ttl))))
    (.build b)))

(defn- ->prompt ^ResponsePrompt [{:keys [id version variables]}]
  (let [^ResponsePrompt$Builder b (ResponsePrompt/builder)]
    (when-not id (impl/missing-key! :id))
    (.id b ^String id)
    (when version (.version b ^String version))
    (when variables
      (let [^ResponsePrompt$Variables$Builder vb (ResponsePrompt$Variables/builder)]
        (.additionalProperties vb ^java.util.Map (impl/->json-value-properties variables))
        (.variables b (.build vb))))
    (.build b)))

(defn- ->context-management ^ResponseCreateParams$ContextManagement
  [{:keys [type compact-threshold]}]
  (let [^ResponseCreateParams$ContextManagement$Builder b
        (ResponseCreateParams$ContextManagement/builder)]
    (when-not type (impl/missing-key! :type))
    (.type b ^String (impl/enum-name type))
    (when (some? compact-threshold) (.compactThreshold b (long compact-threshold)))
    (.build b)))

(defn- ->function-parameters ^FunctionTool$Parameters [m]
  (let [^FunctionTool$Parameters$Builder b (FunctionTool$Parameters/builder)]
    (doseq [[k v] (walk/stringify-keys m)]
      (.putAdditionalProperty b ^String k (JsonValue/from v)))
    (.build b)))

(defn- ->text-config ^ResponseTextConfig [json-schema verbosity]
  (let [^ResponseTextConfig$Builder tb (ResponseTextConfig/builder)]
    (when json-schema
      (let [{:keys [name schema strict description]} json-schema
            ^ResponseFormatTextJsonSchemaConfig$Schema$Builder sb
            (ResponseFormatTextJsonSchemaConfig$Schema/builder)
            ^ResponseFormatTextJsonSchemaConfig$Builder fb
            (ResponseFormatTextJsonSchemaConfig/builder)]
        (when-not name (impl/missing-key! :name))
        (when-not schema (impl/missing-key! :schema))
        (.additionalProperties sb ^java.util.Map (impl/->json-schema-properties schema))
        (.name fb ^String name)
        (.schema fb (.build sb))
        (when description (.description fb ^String description))
        (when (some? strict) (.strict fb (boolean strict)))
        (.format tb (.build fb))))
    (when verbosity
      (.verbosity tb (ResponseTextConfig$Verbosity/of (name verbosity))))
    (.build tb)))

(defn- ->stream-options ^ResponseCreateParams$StreamOptions [{:keys [include-obfuscation]}]
  (let [^ResponseCreateParams$StreamOptions$Builder b (ResponseCreateParams$StreamOptions/builder)]
    (when (some? include-obfuscation)
      (.includeObfuscation b (boolean include-obfuscation)))
    (.build b)))

(defn- ->moderation ^ResponseCreateParams$Moderation [{:keys [model]}]
  (let [^ResponseCreateParams$Moderation$Builder b (ResponseCreateParams$Moderation/builder)]
    (when model (.model b ^String model))
    (.build b)))

(defn- ->function-tool ^FunctionTool [{:keys [name description parameters strict]}]
  (let [^FunctionTool$Builder b (FunctionTool/builder)]
    (when-not name (impl/missing-key! :name))
    (.name b ^String name)
    (when description (.description b ^String description))
    (when parameters (.parameters b (->function-parameters parameters)))
    (when (some? strict) (.strict b (boolean strict)))
    (.build b)))

(defn- ->code-interpreter ^Tool$CodeInterpreter [{:keys [container]}]
  (let [^Tool$CodeInterpreter$Builder b (Tool$CodeInterpreter/builder)]
    (if container
      (.container b ^String container)
      (.container b
                  (let [^Tool$CodeInterpreter$Container$CodeInterpreterToolAuto$Builder ab
                        (Tool$CodeInterpreter$Container$CodeInterpreterToolAuto/builder)]
                    (.build ab))))
    (.build b)))

(defn- ->web-search-filters ^WebSearchTool$Filters [{:keys [allowed-domains]}]
  (let [^WebSearchTool$Filters$Builder b (WebSearchTool$Filters/builder)]
    (if (seq allowed-domains)
      (.allowedDomains b ^java.util.List (vec allowed-domains))
      (throw (ex-info "Explicitly empty :allowed-domains is not supported; omit the key to allow all domains."
                      {:openai/error :empty-allow-list :option :allowed-domains})))
    (.build b)))

(defn- ->web-search-user-location ^WebSearchTool$UserLocation
  [{:keys [city country region timezone]}]
  (let [^WebSearchTool$UserLocation$Builder b (WebSearchTool$UserLocation/builder)]
    (when city (.city b ^String city))
    (when country (.country b ^String country))
    (when region (.region b ^String region))
    (when timezone (.timezone b ^String timezone))
    (.build b)))

(defn- ->web-search-tool ^WebSearchTool
  [{:keys [search-context-size user-location allowed-domains] :as tool}]
  (let [^WebSearchTool$Builder b (WebSearchTool/builder)]
    (.type b WebSearchTool$Type/WEB_SEARCH)
    (when search-context-size
      (.searchContextSize b (WebSearchTool$SearchContextSize/of (name search-context-size))))
    (when user-location (.userLocation b (->web-search-user-location user-location)))
    (when (contains? tool :allowed-domains)
      (.filters b (->web-search-filters {:allowed-domains allowed-domains})))
    (.build b)))

(defn- ->comparison-filter ^ComparisonFilter [{:keys [type key value]}]
  (let [^ComparisonFilter$Builder b (ComparisonFilter/builder)]
    (when-not type (impl/missing-key! :type))
    (when-not key (impl/missing-key! :key))
    (.type b (ComparisonFilter$Type/of (name type)))
    (.key b ^String key)
    (cond
      (string? value) (.value b ^String value)
      (number? value) (.value b (double value))
      (instance? Boolean value) (.value b (boolean value))
      :else (.value b ^String (str value)))
    (.build b)))

(defn- filter->plain
  "A filter map as plain JSON-shaped data. Use it to nest a compound filter
  inside another compound filter. The SDK models only one level natively."
  [{:keys [type key value filters]}]
  (if filters
    {"type" (name type) "filters" (mapv filter->plain filters)}
    {"type" (name type) "key" key "value" value}))

(defn- ->compound-filter ^CompoundFilter [{:keys [type filters]}]
  (when-not type (impl/missing-key! :type))
  (let [^CompoundFilter$Builder b (CompoundFilter/builder)]
    (.type b (CompoundFilter$Type/of (name type)))
    (doseq [f filters]
      (if (:filters f)
        (.addFilter b (JsonValue/from (filter->plain f)))
        (.addFilter b (->comparison-filter f))))
    (.build b)))

(defn- ->file-search-filters ^FileSearchTool$Filters [filters]
  (if (:filters filters)
    (FileSearchTool$Filters/ofCompoundFilter (->compound-filter filters))
    (FileSearchTool$Filters/ofComparisonFilter (->comparison-filter filters))))

(defn- ->ranking-options ^FileSearchTool$RankingOptions [{:keys [ranker score-threshold]}]
  (let [^FileSearchTool$RankingOptions$Builder b (FileSearchTool$RankingOptions/builder)]
    (when ranker (.ranker b (FileSearchTool$RankingOptions$Ranker/of (str ranker))))
    (when (some? score-threshold) (.scoreThreshold b (double score-threshold)))
    (.build b)))

(defn- ->file-search-tool ^FileSearchTool
  [{:keys [vector-store-ids max-num-results filters ranking-options]}]
  (let [^FileSearchTool$Builder b (FileSearchTool/builder)]
    (when-not (seq vector-store-ids) (impl/missing-key! :vector-store-ids))
    (.vectorStoreIds b ^java.util.List (vec vector-store-ids))
    (when max-num-results (.maxNumResults b (long max-num-results)))
    (when filters (.filters b (->file-search-filters filters)))
    (when ranking-options (.rankingOptions b (->ranking-options ranking-options)))
    (.build b)))

(defn- ->mcp-headers ^Tool$Mcp$Headers [headers]
  (let [^Tool$Mcp$Headers$Builder b (Tool$Mcp$Headers/builder)]
    (.additionalProperties b ^java.util.Map (impl/->json-value-properties headers))
    (.build b)))

(defn- ->mcp-tool ^Tool$Mcp
  [{:keys [server-label server-url allowed-tools require-approval headers] :as tool}]
  (let [^Tool$Mcp$Builder b (Tool$Mcp/builder)]
    (when-not server-label (impl/missing-key! :server-label))
    (.serverLabel b ^String server-label)
    (when server-url (.serverUrl b ^String server-url))
    (when (contains? tool :allowed-tools)
      (if (seq allowed-tools)
        (.allowedToolsOfMcp b ^java.util.List (vec allowed-tools))
        (throw (ex-info "Explicitly empty :allowed-tools is not supported; omit the key to allow all tools."
                        {:openai/error :empty-allow-list :option :allowed-tools}))))
    (when require-approval
      (.requireApproval b (Tool$Mcp$RequireApproval$McpToolApprovalSetting/of (name require-approval))))
    (when headers (.headers b (->mcp-headers headers)))
    (.build b)))

(defn- ->image-generation-tool
  [{:keys [action background input-fidelity model moderation output-compression
           output-format partial-images quality size]}]
  (let [b (Tool$ImageGeneration/builder)]
    (when action (.action b (Tool$ImageGeneration$Action/of (impl/enum-name action))))
    (when background (.background b (Tool$ImageGeneration$Background/of (impl/enum-name background))))
    (when input-fidelity (.inputFidelity b (Tool$ImageGeneration$InputFidelity/of (impl/enum-name input-fidelity))))
    (when model (.model b ^String model))
    (when moderation (.moderation b (Tool$ImageGeneration$Moderation/of (impl/enum-name moderation))))
    (when output-compression (.outputCompression b (long output-compression)))
    (when output-format (.outputFormat b (Tool$ImageGeneration$OutputFormat/of (impl/enum-name output-format))))
    (when partial-images (.partialImages b (long partial-images)))
    (when quality (.quality b (Tool$ImageGeneration$Quality/of (impl/enum-name quality))))
    (when size (.size b ^String size))
    (.build b)))

(defn- ->shell-tool [{:keys [environment container-id]}]
  (let [b (com.openai.models.responses.FunctionShellTool/builder)]
    (case (keyword environment)
      :local (.environment b (.build (com.openai.models.responses.LocalEnvironment/builder)))
      :container-auto (.environment b (.build (com.openai.models.responses.ContainerAuto/builder)))
      nil nil
      (throw (ex-info (str "Unknown shell environment " environment)
                      {:openai/error :unknown-shell-environment :environment environment})))
    (when container-id (.containerReferenceEnvironment b ^String container-id))
    (.build b)))

(defn- ->custom-tool [{:keys [name description format defer-loading]}]
  (when-not name (impl/missing-key! :name))
  (let [b (com.openai.models.responses.CustomTool/builder)]
    (.name b ^String name)
    (when description (.description b ^String description))
    (when (some? defer-loading) (.deferLoading b (boolean defer-loading)))
    (cond
      (= :text (keyword format)) (.formatText b)
      (map? format)
      (let [{:keys [definition syntax]} format
            grammar (com.openai.models.CustomToolInputFormat$Grammar/builder)]
        (when-not definition (impl/missing-key! :definition))
        (when-not syntax (impl/missing-key! :syntax))
        (.definition grammar ^String definition)
        (.syntax grammar (com.openai.models.CustomToolInputFormat$Grammar$Syntax/of
                          (impl/enum-name syntax)))
        (.format b (.build grammar))))
    (.build b)))

(defn- ->tool-search-tool [{:keys [description execution parameters]}]
  (when-not parameters (impl/missing-key! :parameters))
  (let [b (com.openai.models.responses.ToolSearchTool/builder)]
    (.parameters b (JsonValue/from (walk/stringify-keys parameters)))
    (when description (.description b ^String description))
    (when execution
      (.execution b (com.openai.models.responses.ToolSearchTool$Execution/of
                     (impl/enum-name execution))))
    (.build b)))

(defn- ->tool ^Tool [{:keys [type] :as tool}]
  (case (keyword type)
    :function (Tool/ofFunction (->function-tool tool))
    :web-search (Tool/ofWebSearch (->web-search-tool tool))
    :file-search (Tool/ofFileSearch (->file-search-tool tool))
    :mcp (Tool/ofMcp (->mcp-tool tool))
    :code-interpreter (Tool/ofCodeInterpreter (->code-interpreter tool))
    :programmatic-tool-calling (Tool/ofProgrammaticToolCalling)
    :image-generation (Tool/ofImageGeneration (->image-generation-tool tool))
    :computer (Tool/ofComputer (.build (com.openai.models.responses.ComputerTool/builder)))
    :local-shell (Tool/ofLocalShell)
    :shell (Tool/ofShell (->shell-tool tool))
    :apply-patch (Tool/ofApplyPatch (.build (com.openai.models.responses.ApplyPatchTool/builder)))
    :custom (Tool/ofCustom (->custom-tool tool))
    :tool-search (Tool/ofSearch (->tool-search-tool tool))
    (throw (ex-info (str "Unknown tool type " type)
                    {:openai/error :unknown-tool-type :type type}))))

(defn- ->tool-choice-function ^ToolChoiceFunction [{:keys [name]}]
  (let [^ToolChoiceFunction$Builder b (ToolChoiceFunction/builder)]
    (when-not name (impl/missing-key! :name))
    (.name b ^String name)
    (.build b)))

(defn- ->tool-choice-option ^ToolChoiceOptions [choice]
  (case (keyword choice)
    :auto ToolChoiceOptions/AUTO
    :required ToolChoiceOptions/REQUIRED
    :none ToolChoiceOptions/NONE
    (throw (ex-info (str "Unknown tool choice " choice)
                    {:openai/error :unknown-tool-choice
                     :tool-choice choice}))))

(defn- ->tool-choice ^ResponseCreateParams$ToolChoice [choice]
  (if (map? choice)
    (case (keyword (:type choice))
      :function (ResponseCreateParams$ToolChoice/ofFunction
                 (->tool-choice-function choice))
      :programmatic-tool-calling
      (ResponseCreateParams$ToolChoice/ofSpecificProgrammaticToolCallingParam)
      (throw (ex-info (str "Unknown tool choice type " (:type choice))
                      {:openai/error :unknown-tool-choice-type
                       :type (:type choice)})))
    (ResponseCreateParams$ToolChoice/ofOptions (->tool-choice-option choice))))

(defn- ->params ^ResponseCreateParams
  [{:keys [model input instructions max-output-tokens temperature top-p
           metadata previous-response-id store reasoning user tools tool-choice
           parallel-tool-calls background include truncation prompt-cache-key prompt-cache-options
           safety-identifier service-tier max-tool-calls top-logprobs
           json-schema verbosity conversation stream-options moderation prompt
           context-management prompt-cache-retention]}]
  (when-not model (impl/missing-key! :model))
  (when-not input (impl/missing-key! :input))
  (let [^ResponseCreateParams$Builder b (ResponseCreateParams/builder)]
    (.model b ^String model)
    (.input b (->input input))
    (when instructions (.instructions b ^String instructions))
    (when max-output-tokens (.maxOutputTokens b (long max-output-tokens)))
    (when max-tool-calls (.maxToolCalls b (long max-tool-calls)))
    (when temperature (.temperature b (double temperature)))
    (when top-p (.topP b (double top-p)))
    (when top-logprobs (.topLogprobs b (long top-logprobs)))
    (when (some? background) (.background b (boolean background)))
    (doseq [i include] (.addInclude b (ResponseIncludable/of (impl/enum-name i))))
    (when truncation (.truncation b (ResponseCreateParams$Truncation/of (impl/enum-name truncation))))
    (when prompt-cache-key (.promptCacheKey b ^String prompt-cache-key))
    (when prompt-cache-options (.promptCacheOptions b (->prompt-cache-options prompt-cache-options)))
    (when prompt (.prompt b (->prompt prompt)))
    (doseq [context context-management]
      (.addContextManagement b (->context-management context)))
    (when prompt-cache-retention
      (.promptCacheRetention b (ResponseCreateParams$PromptCacheRetention/of
                                (if (keyword? prompt-cache-retention)
                                  (name prompt-cache-retention)
                                  (str prompt-cache-retention)))))
    (when safety-identifier (.safetyIdentifier b ^String safety-identifier))
    (when service-tier (.serviceTier b (ResponseCreateParams$ServiceTier/of (impl/enum-name service-tier))))
    (when metadata (.metadata b (->metadata metadata)))
    (when previous-response-id (.previousResponseId b ^String previous-response-id))
    (when (some? store) (.store b (boolean store)))
    (when reasoning (.reasoning b (->reasoning reasoning)))
    (when user (.user b ^String user))
    (doseq [t tools] (.addTool b (->tool t)))
    (when tool-choice (.toolChoice b (->tool-choice tool-choice)))
    (when (some? parallel-tool-calls)
      (.parallelToolCalls b (boolean parallel-tool-calls)))
    (when (or json-schema verbosity)
      (.text b (->text-config json-schema verbosity)))
    (when conversation (.conversation b ^String conversation))
    (when stream-options (.streamOptions b (->stream-options stream-options)))
    (when moderation (.moderation b (->moderation moderation)))
    (.build b)))

(defn- ->input-token-count-params ^InputTokenCountParams
  [{:keys [model input instructions previous-response-id reasoning tools tool-choice
           parallel-tool-calls truncation]}]
  (let [^InputTokenCountParams$Builder b (InputTokenCountParams/builder)]
    (when model (.model b ^String model))
    (when input
      (if (string? input)
        (.input b ^String input)
        (.inputOfResponseInputItems b ^java.util.List (mapv response-input-item input))))
    (when instructions (.instructions b ^String instructions))
    (when previous-response-id (.previousResponseId b ^String previous-response-id))
    (when reasoning (.reasoning b (->reasoning reasoning)))
    (doseq [t tools] (.addTool b (->tool t)))
    (when tool-choice
      (if (map? tool-choice)
        (case (keyword (:type tool-choice))
          :function (.toolChoice b (->tool-choice-function tool-choice))
          (throw (ex-info (str "Unknown tool choice type " (:type tool-choice))
                          {:openai/error :unknown-tool-choice-type
                           :type (:type tool-choice)})))
        (.toolChoice b (->tool-choice-option tool-choice))))
    (when (some? parallel-tool-calls) (.parallelToolCalls b (boolean parallel-tool-calls)))
    (when truncation (.truncation b (InputTokenCountParams$Truncation/of (impl/enum-name truncation))))
    (.build b)))

(defn- annotation->map [^ResponseOutputText$Annotation a]
  (cond
    (.isUrlCitation a) (let [^ResponseOutputText$Annotation$UrlCitation u (.asUrlCitation a)]
                         {:type :url-citation
                          :url (.url u)
                          :title (.title u)
                          :start-index (.startIndex u)
                          :end-index (.endIndex u)})
    (.isFileCitation a) (let [^ResponseOutputText$Annotation$FileCitation f (.asFileCitation a)]
                          {:type :file-citation
                           :file-id (.fileId f)
                           :filename (.filename f)
                           :index (.index f)})
    (.isContainerFileCitation a) (let [^ResponseOutputText$Annotation$ContainerFileCitation f
                                       (.asContainerFileCitation a)]
                                   {:type :container-file-citation
                                    :container-id (.containerId f)
                                    :file-id (.fileId f)
                                    :filename (.filename f)
                                    :start-index (.startIndex f)
                                    :end-index (.endIndex f)})
    (.isFilePath a) (let [^ResponseOutputText$Annotation$FilePath f (.asFilePath a)]
                      {:type :file-path
                       :file-id (.fileId f)
                       :index (.index f)})
    :else {:type :unknown}))

(defn- top-logprob->map [^ResponseOutputText$Logprob$TopLogprob l]
  {:token (.token l)
   :bytes (vec (.bytes l))
   :logprob (.logprob l)})

(defn- logprob->map [^ResponseOutputText$Logprob l]
  {:token (.token l)
   :bytes (vec (.bytes l))
   :logprob (.logprob l)
   :top-logprobs (mapv top-logprob->map (.topLogprobs l))})

(defn- content->map [^ResponseOutputMessage$Content c]
  (cond
    (.isOutputText c) (let [^ResponseOutputText t (.asOutputText c)]
                        (cond-> {:type :text :text (.text t)}
                          (seq (.annotations t)) (assoc :annotations (mapv annotation->map (.annotations t)))
                          (.isPresent (.logprobs t)) (assoc :logprobs (mapv logprob->map (.get (.logprobs t))))))
    (.isRefusal c) (let [^ResponseOutputRefusal r (.asRefusal c)]
                     {:type :refusal :refusal (.refusal r)})
    :else {:type :unknown}))

(defn- message->map [^ResponseOutputMessage m]
  {:type :message
   :role :assistant
   :id (.id m)
   :content (mapv content->map (.content m))})

(declare sdk-output->map)

(defn- function-call->map [^ResponseFunctionToolCall f]
  (assoc (sdk-output->map :function-call f)
         :arguments (impl/parse-arguments (.arguments f))))

(defn- reasoning->map [^ResponseReasoningItem r]
  (cond-> {:type :reasoning
           :id (.id r)}
    (.isPresent (.status r)) (assoc :status (impl/->keyword (.asString ^ResponseReasoningItem$Status (.get (.status r)))))
    (seq (.summary r)) (assoc :summary (mapv #(.text ^ResponseReasoningItem$Summary %) (.summary r)))
    (.isPresent (.content r)) (assoc :content (impl/sdk-object->clj (.get (.content r))))
    (.isPresent (.encryptedContent r)) (assoc :encrypted-content (.get (.encryptedContent r)))))

(defn- sdk-output->map [type value]
  (let [m (assoc (impl/sdk-object->clj value) :type type)]
    (cond-> m
      (string? (:status m)) (update :status impl/->keyword)
      (string? (:execution m)) (update :execution impl/->keyword))))

(defn- web-search-call->map [^ResponseFunctionWebSearch c]
  (sdk-output->map :web-search-call c))

(defn- file-search-call->map [^com.openai.models.responses.ResponseFileSearchToolCall c]
  (sdk-output->map :file-search-call c))

(defn- code-interpreter-call->map [^com.openai.models.responses.ResponseCodeInterpreterToolCall c]
  (sdk-output->map :code-interpreter-call c))

(defn- image-generation-call->map [^ResponseOutputItem$ImageGenerationCall c]
  (sdk-output->map :image-generation-call c))

(defn- mcp-call->map [^ResponseOutputItem$McpCall c]
  (sdk-output->map :mcp-call c))

(defn- mcp-list-tools->map [^ResponseOutputItem$McpListTools c]
  (sdk-output->map :mcp-list-tools c))

(defn- mcp-approval-request->map [^ResponseOutputItem$McpApprovalRequest c]
  (sdk-output->map :mcp-approval-request c))

(defn- custom-tool-call->map [^com.openai.models.responses.ResponseCustomToolCall c]
  (sdk-output->map :custom-tool-call c))

(defn- local-shell-call->map [^ResponseOutputItem$LocalShellCall c]
  (sdk-output->map :local-shell-call c))

(defn- computer-call->map [^com.openai.models.responses.ResponseComputerToolCall c]
  (sdk-output->map :computer-call c))

(defn- output-item->map [^ResponseOutputItem item]
  (cond
    (.isMessage item) (message->map (.asMessage item))
    (.isFunctionCall item) (function-call->map (.asFunctionCall item))
    (.isReasoning item) (reasoning->map (.asReasoning item))
    (.isWebSearchCall item) (web-search-call->map (.asWebSearchCall item))
    (.isFileSearchCall item) (file-search-call->map (.asFileSearchCall item))
    (.isCodeInterpreterCall item) (code-interpreter-call->map (.asCodeInterpreterCall item))
    (.isImageGenerationCall item) (image-generation-call->map (.asImageGenerationCall item))
    (.isMcpCall item) (mcp-call->map (.asMcpCall item))
    (.isMcpListTools item) (mcp-list-tools->map (.asMcpListTools item))
    (.isMcpApprovalRequest item) (mcp-approval-request->map (.asMcpApprovalRequest item))
    (.isCustomToolCall item) (custom-tool-call->map (.asCustomToolCall item))
    (.isLocalShellCall item) (local-shell-call->map (.asLocalShellCall item))
    (.isComputerCall item) (computer-call->map (.asComputerCall item))
    (.isFunctionCallOutput item) (sdk-output->map :function-call-output (.asFunctionCallOutput item))
    (.isComputerCallOutput item) (sdk-output->map :computer-call-output (.asComputerCallOutput item))
    (.isProgram item) (sdk-output->map :program (.asProgram item))
    (.isProgramOutput item) (sdk-output->map :program-output (.asProgramOutput item))
    (.isToolSearchCall item) (sdk-output->map :tool-search-call (.asToolSearchCall item))
    (.isToolSearchOutput item) (sdk-output->map :tool-search-output (.asToolSearchOutput item))
    (.isAdditionalTools item) (sdk-output->map :additional-tools (.asAdditionalTools item))
    (.isCompaction item) (sdk-output->map :compaction (.asCompaction item))
    (.isLocalShellCallOutput item) (sdk-output->map :local-shell-call-output (.asLocalShellCallOutput item))
    (.isShellCall item) (sdk-output->map :shell-call (.asShellCall item))
    (.isShellCallOutput item) (sdk-output->map :shell-call-output (.asShellCallOutput item))
    (.isApplyPatchCall item) (sdk-output->map :apply-patch-call (.asApplyPatchCall item))
    (.isApplyPatchCallOutput item) (sdk-output->map :apply-patch-call-output (.asApplyPatchCallOutput item))
    (.isMcpApprovalResponse item) (sdk-output->map :mcp-approval-response (.asMcpApprovalResponse item))
    (.isCustomToolCallOutput item) (sdk-output->map :custom-tool-call-output (.asCustomToolCallOutput item))
    :else {:type :unknown}))

(defn- input-message-item->map [^ResponseInputMessageItem m]
  (cond-> {:type :message
           :role (impl/->keyword (.asString (.role m)))
           :id (.id m)}
    (.isPresent (.status m)) (assoc :status (impl/->keyword (.asString ^com.openai.models.responses.ResponseInputMessageItem$Status (.get (.status m)))))))

(defn- function-call-output->clj
  "A stored function-call-output carries a union: a plain string or a list of
  content parts. Unwrap the string so the value round-trips into a request.
  Convert the content list instead of exposing the union `toString`."
  [^com.openai.models.responses.ResponseFunctionToolCallOutputItem$Output o]
  (if (.isString o)
    (.asString o)
    (impl/sdk-object->clj o)))

(defn- response-item->map [^ResponseItem item]
  (cond
    (.isResponseInputMessageItem item) (input-message-item->map (.asResponseInputMessageItem item))
    (.isResponseOutputMessage item) (message->map (.asResponseOutputMessage item))
    (.isFunctionCall item) (function-call->map (.toResponseFunctionToolCall (.asFunctionCall item)))
    (.isFunctionCallOutput item) (let [c (.asFunctionCallOutput item)]
                                   (cond-> {:type :function-call-output
                                            :id (.id c)
                                            :status (impl/->keyword (.asString (.status c)))
                                            :output (function-call-output->clj (.output c))}
                                     (.isPresent (.callId c)) (assoc :call-id (.get (.callId c)))
                                     (.isPresent (.name c)) (assoc :name (.get (.name c)))
                                     (.isPresent (.namespace c)) (assoc :namespace (.get (.namespace c)))))
    :else {:type :unknown}))

(defn- usage->map [^ResponseUsage u]
  (let [details (.inputTokensDetails u)]
    (cond-> {:input-tokens (.inputTokens u)
             :input-tokens-details
             {:cache-write-tokens (.cacheWriteTokens details)
              :cached-tokens (.cachedTokens details)}
             :output-tokens (.outputTokens u)
             :total-tokens (.totalTokens u)}
      (.isPresent (.computeUnits u)) (assoc :compute-units (.get (.computeUnits u))))))

(defn- error->map [^ResponseError e]
  {:code (impl/->keyword (.asString (.code e)))
   :message (.message e)})

(defn- incomplete-details->map [^Response$IncompleteDetails d]
  (cond-> {}
    (.isPresent (.reason d)) (assoc :reason (impl/->keyword (.asString ^com.openai.models.responses.Response$IncompleteDetails$Reason (.get (.reason d)))))))

(defn- response->map
  ([^Response r] (response->map r {}))
  ([^Response r opts]
   (let [items (mapv output-item->map (.output r))]
    (impl/preserve-raw
     (cond-> {:id (.id r)
             :model (.asString ^ResponsesModel (.model r))
             :output items
             :text (impl/output-text items)
             :created-at (.createdAt r)}
      (.isPresent (.status r)) (assoc :status (impl/->keyword (.asString ^ResponseStatus (.get (.status r)))))
      (.isPresent (.usage r)) (assoc :usage (usage->map (.get (.usage r))))
      (.isPresent (.error r)) (assoc :error (error->map (.get (.error r))))
      (.isPresent (.incompleteDetails r)) (assoc :incomplete-details (incomplete-details->map (.get (.incompleteDetails r))))
      (.isPresent (.previousResponseId r)) (assoc :previous-response-id (.get (.previousResponseId r)))
      (.isPresent (.prompt r)) (assoc :prompt (impl/sdk-object->clj (.get (.prompt r))))
      (.isPresent (.promptCacheRetention r))
      (assoc :prompt-cache-retention
             (.asString ^com.openai.models.responses.Response$PromptCacheRetention
                        (.get (.promptCacheRetention r)))))
     r opts))))

(defn- schema-value [schema key]
  (if (contains? schema key)
    (get schema key)
    (get schema (name key))))

(defn- common-constraint-errors [schema data path]
  (let [minimum (schema-value schema :minimum)
        maximum (schema-value schema :maximum)
        min-length (schema-value schema :minLength)
        max-length (schema-value schema :maxLength)
        min-items (schema-value schema :minItems)
        max-items (schema-value schema :maxItems)
        actual-length (when (string? data)
                        (.codePointCount ^String data 0 (.length ^String data)))
        properties (or (schema-value schema :properties) {})
        items (schema-value schema :items)]
    (vec
     (concat
      (when (and (number? data) (some? minimum) (< data minimum))
        [{:path path :error :minimum :minimum minimum :actual data}])
      (when (and (number? data) (some? maximum) (> data maximum))
        [{:path path :error :maximum :maximum maximum :actual data}])
      (when (and (some? actual-length) (some? min-length) (< actual-length min-length))
        [{:path path :error :min-length :minimum min-length :actual actual-length}])
      (when (and (some? actual-length) (some? max-length) (> actual-length max-length))
        [{:path path :error :max-length :maximum max-length :actual actual-length}])
      (when (and (sequential? data) (some? min-items) (< (count data) min-items))
        [{:path path :error :min-items :minimum min-items :actual (count data)}])
      (when (and (sequential? data) (some? max-items) (> (count data) max-items))
        [{:path path :error :max-items :maximum max-items :actual (count data)}])
      (when (map? data)
        (mapcat (fn [[key child-schema]]
                  (let [data-key (if (keyword? key) key (keyword (str key)))]
                    (when (contains? data data-key)
                      (common-constraint-errors child-schema (get data data-key)
                                                (conj path data-key)))))
                properties))
      (when (and (sequential? data) items)
        (mapcat (fn [[index value]]
                  (common-constraint-errors items value (conj path index)))
                (map-indexed vector data)))))))

(defn parse-structured-output
  "Parse a Responses `:text` value and validate it against a `:json-schema`
  config (or a raw schema). Returns `{:data ... :errors [...]}`."
  [response json-schema]
  (let [schema (or (:schema json-schema) (get json-schema "schema") json-schema)]
    (try
      (let [data (impl/parse-json (:text response))]
        {:data data
         :errors (into (impl/validate-json-schema schema data)
                       (common-constraint-errors schema data []))})
      (catch Exception e
        {:data nil
         :errors [{:path [] :error :invalid-json :message (.getMessage e)}]}))))

(defn create-response
  "Send a Responses API request and return a Clojure map.

  Request keys: `:model` (required string), `:input` (required string or vector),
  `:instructions`, `:max-output-tokens`, `:temperature`, `:top-p`, `:metadata`,
  `:previous-response-id`, `:store`, `:reasoning`, `:user`, `:tools`,
  `:tool-choice`, `:parallel-tool-calls`, `:background`, `:include`,
  `:truncation`, `:prompt-cache-key`, `:safety-identifier`, `:service-tier`,
  `:max-tool-calls`, `:top-logprobs`, `:json-schema`, `:verbosity`,
  `:conversation`, `:stream-options`, `:moderation`, `:prompt`,
  `:context-management`, and `:prompt-cache-retention`.

  Message-vector input items accept `{:role :system|:developer|:user|:assistant
  :content \"...\"}`, multimodal content vectors containing text, image, or file
  part maps, and `{:type :function-call-output :call-id \"...\" :name \"...\"
  :namespace \"...\" :output \"...\"}`. `:name` and `:namespace` are optional.
  Map outputs are JSON-encoded.

  Structured outputs: `:json-schema {:name \"...\" :schema {...} :strict true
  :description \"...\"}`.

  Set `:lossless? true` to add the complete parsed SDK response under
  `:openai/raw` while retaining the curated top-level map.

  Tools: `{:type :function :name \"...\" :description \"...\" :strict true
  :parameters {...}}`, `{:type :web-search}`, `{:type :file-search
  :vector-store-ids [...]}`, or `{:type :code-interpreter :container \"...\"}`.
  Code interpreter defaults to an auto container when `:container` is omitted.

  Tool choice: `:auto`, `:required`, `:none`, or `{:type :function :name \"...\"}`.

  Returns `{:id :model :status :output :text :usage :created-at}` plus
  `:error`, `:incomplete-details`, or `:previous-response-id` when present.
  Output items are normalized to `:message`, `:function-call`, `:reasoning`,
  `:web-search-call`, `:file-search-call`, `:code-interpreter-call`, or
  `:unknown`."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (response->map (.create (.responses client) (->params req)) req)))

(defn count-input-tokens
  "Count input tokens for a Responses request shape. It accepts the same request
  map as `create-response`. It ignores fields the SDK input token count endpoint
  does not support. Returns `{:input-tokens n}`."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ResponseService svc (.responses client)
          ^InputTokenService tokens (.inputTokens svc)
          ^InputTokenCountResponse r (.count tokens (->input-token-count-params req))]
      {:input-tokens (.inputTokens r)})))

(defn- model->map [^Model m]
  {:id (.id m)
   :created (.created m)
   :owned-by (.ownedBy m)})

(defn- ->embedding-params ^EmbeddingCreateParams
  [{:keys [model input dimensions user]}]
  (when-not model (impl/missing-key! :model))
  (when-not input (impl/missing-key! :input))
  (let [^EmbeddingCreateParams$Builder b (EmbeddingCreateParams/builder)]
    (.model b ^String model)
    (if (string? input)
      (.input b (EmbeddingCreateParams$Input/ofString input))
      (.input b (EmbeddingCreateParams$Input/ofArrayOfStrings ^java.util.List (vec input))))
    (when dimensions (.dimensions b (long dimensions)))
    (when user (.user b ^String user))
    (.build b)))

(defn- embedding-response->map [^CreateEmbeddingResponse r]
  (let [^CreateEmbeddingResponse$Usage u (.usage r)]
    {:model (.model r)
     :embeddings (->> (.data r)
                      (sort-by (fn [^Embedding e] (.index e)))
                      (mapv (fn [^Embedding e] (vec (.embedding e)))))
     :usage {:prompt-tokens (.promptTokens u)
             :total-tokens (.totalTokens u)}}))

(defn create-embeddings
  "Create embeddings for `:input` (a string, or a vector of strings) with
  `:model` (required, e.g. \"text-embedding-3-small\"). Optional: `:dimensions`
  (truncated output size, supported by v3 models) and `:user`.

  Returns `{:model \"...\" :embeddings [[floats ...] ...] :usage
  {:prompt-tokens n :total-tokens n}}`; `:embeddings` is ordered to match the
  input order (one vector even for string input)."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^EmbeddingService svc (.embeddings client)]
      (embedding-response->map (.create svc (->embedding-params req))))))

(defn list-models
  "List available models as a vector of `{:id :created :owned-by}` maps. It
  follows each page automatically."
  [^OpenAIClient client]
  (impl/with-api-errors
    (let [^ModelService svc (.models client)
          ^ModelListPage p (.list svc)]
      (mapv model->map (impl/all-pages p)))))

(defn get-model
  "Retrieve one model by id as a `{:id :created :owned-by}` map."
  [^OpenAIClient client ^String model-id]
  (impl/with-api-errors
    (let [^ModelService svc (.models client)]
      (model->map (.retrieve svc model-id)))))

(defn list-models-lazy
  "Lazy sibling of `list-models`; accepts optional `:max-items` and `:max-pages`."
  ([^OpenAIClient client] (list-models-lazy client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^ModelService svc (.models client)
           ^ModelListPage p (.list svc)]
       (map model->map (impl/lazy-pages p opts))))))

(defn- ->model-delete-params ^ModelDeleteParams [^String model-id]
  (-> (ModelDeleteParams/builder)
      (.model model-id)
      (.build)))

(defn- deleted-model->map [^ModelDeleted m]
  {:id (.id m)
   :deleted (.deleted m)})

(defn delete-model
  "Delete a fine-tuned model and return `{:id :deleted}`."
  [^OpenAIClient client ^String model-id]
  (impl/with-api-errors
    (let [^ModelService svc (.models client)]
      (deleted-model->map (.delete svc (->model-delete-params model-id))))))

(defn- event->map
  "Normalize one `ResponseStreamEvent` into a Clojure map keyed by `:type`."
  [^ResponseStreamEvent ev]
  (cond
    (.isOutputTextDelta ev) (let [e ^ResponseTextDeltaEvent (.asOutputTextDelta ev)]
                              {:type :output-text-delta
                               :delta (.delta e)
                               :item-id (.itemId e)
                               :output-index (.outputIndex e)})
    (.isOutputTextDone ev) (let [e ^ResponseTextDoneEvent (.asOutputTextDone ev)]
                             {:type :output-text-done
                              :text (.text e)
                              :item-id (.itemId e)
                              :output-index (.outputIndex e)})
    (.isFunctionCallArgumentsDelta ev)
    (let [e ^ResponseFunctionCallArgumentsDeltaEvent (.asFunctionCallArgumentsDelta ev)]
      {:type :function-call-arguments-delta
       :delta (.delta e)
       :item-id (.itemId e)})
    (.isFunctionCallArgumentsDone ev)
    (let [e ^ResponseFunctionCallArgumentsDoneEvent (.asFunctionCallArgumentsDone ev)]
      {:type :function-call-arguments-done
       :arguments (.arguments e)
       :item-id (.itemId e)})
    (.isReasoningTextDelta ev) (let [e ^ResponseReasoningTextDeltaEvent (.asReasoningTextDelta ev)]
                                 {:type :reasoning-text-delta
                                  :delta (.delta e)})
    (.isReasoningTextDone ev) (let [e ^ResponseReasoningTextDoneEvent (.asReasoningTextDone ev)]
                                {:type :reasoning-text-done
                                 :text (.text e)})
    (.isRefusalDelta ev) (let [e ^ResponseRefusalDeltaEvent (.asRefusalDelta ev)]
                           {:type :refusal-delta
                            :delta (.delta e)})
    (.isRefusalDone ev) (let [e ^ResponseRefusalDoneEvent (.asRefusalDone ev)]
                          {:type :refusal-done
                           :refusal (.refusal e)})
    (.isOutputItemAdded ev) (let [e ^ResponseOutputItemAddedEvent (.asOutputItemAdded ev)]
                              {:type :output-item-added
                               :item (output-item->map (.item e))
                               :output-index (.outputIndex e)})
    (.isOutputItemDone ev) (let [e ^ResponseOutputItemDoneEvent (.asOutputItemDone ev)]
                             {:type :output-item-done
                              :item (output-item->map (.item e))
                              :output-index (.outputIndex e)})
    (.isCreated ev) {:type :created}
    (.isInProgress ev) {:type :in-progress}
    (.isCompleted ev) (let [e ^ResponseCompletedEvent (.asCompleted ev)]
                        {:type :completed
                         :response (response->map (.response e))})
    (.isIncomplete ev) (let [e ^ResponseIncompleteEvent (.asIncomplete ev)]
                         {:type :incomplete
                          :response (response->map (.response e))})
    (.isFailed ev) (let [e ^ResponseFailedEvent (.asFailed ev)]
                     {:type :failed
                      :response (response->map (.response e))})
    (.isError ev) (let [e ^ResponseErrorEvent (.asError ev)
                        code (.code e)]
                    (cond-> {:type :error
                             :message (.message e)}
                      (.isPresent code) (assoc :code (.get code))))
    (.isQueued ev) (let [e (.asQueued ev)]
                     {:type :queued
                      :response (response->map (.response e))})
    :else (let [m (impl/sdk-object->clj ev)]
            (update m :type #(-> %
                                 (str/replace #"^response\." "")
                                 (str/replace "." "-")
                                 impl/->keyword)))))

(defn- drain-stream
  ^String [^StreamResponse sr on-event]
  (let [sb (StringBuilder.)]
    (doseq [ev (iterator-seq (.iterator (.stream sr)))]
      (let [m (event->map ev)]
        (when (= :output-text-delta (:type m))
          (.append sb ^String (:delta m)))
        (when on-event (on-event m))))
    (str sb)))

(defn stream
  "Stream a Responses API request. It calls `on-event` with a normalized map for
  each server-sent event. It returns concatenated output text. It takes the same
  `req` map as `create-response`. It closes the HTTP stream automatically."
  ^String [^OpenAIClient client req on-event]
  (impl/with-api-errors
    (let [^ResponseService svc (.responses client)]
      (with-open [^StreamResponse sr (.createStreaming svc (->params req))]
        (drain-stream sr on-event)))))

(defn retrieve-streaming
  "Resume the stream for an existing background response id. It calls `on-event`
  with normalized event maps. It returns concatenated output text, as `stream` does."
  ^String [^OpenAIClient client ^String response-id on-event]
  (impl/with-api-errors
    (let [^ResponseService svc (.responses client)]
      (with-open [^StreamResponse sr (.retrieveStreaming svc response-id)]
        (drain-stream sr on-event)))))

(defn stream-text
  "Stream a Responses API request. It calls `on-text` with each output text delta.
  It returns the full concatenated text."
  ^String [^OpenAIClient client req on-text]
  (stream client req
          (fn [m] (when (and on-text (= :output-text-delta (:type m))) (on-text (:delta m))))))

(defn- ->chat-metadata ^ChatCompletionCreateParams$Metadata [m]
  (let [^ChatCompletionCreateParams$Metadata$Builder b (ChatCompletionCreateParams$Metadata/builder)]
    (.additionalProperties b ^java.util.Map (impl/->json-value-properties m))
    (.build b)))

(defn- ->chat-logit-bias ^ChatCompletionCreateParams$LogitBias [m]
  (let [^ChatCompletionCreateParams$LogitBias$Builder b (ChatCompletionCreateParams$LogitBias/builder)]
    (.additionalProperties b ^java.util.Map (impl/->json-value-properties m))
    (.build b)))

(defn- ->chat-content-part-text ^ChatCompletionContentPartText [{:keys [text]}]
  (let [^ChatCompletionContentPartText$Builder b (ChatCompletionContentPartText/builder)]
    (when-not text (impl/missing-key! :text))
    (.text b ^String text)
    (.build b)))

(defn- ->chat-content-part-image ^ChatCompletionContentPartImage [{:keys [image-url detail]}]
  (let [^ChatCompletionContentPartImage$ImageUrl$Builder ub (ChatCompletionContentPartImage$ImageUrl/builder)
        ^ChatCompletionContentPartImage$Builder b (ChatCompletionContentPartImage/builder)]
    (when-not image-url (impl/missing-key! :image-url))
    (.url ub ^String image-url)
    (when detail (.detail ub (ChatCompletionContentPartImage$ImageUrl$Detail/of (name detail))))
    (.imageUrl b (.build ub))
    (.build b)))

(defn- ->chat-content-part-input-audio ^ChatCompletionContentPartInputAudio [{:keys [data format]}]
  (let [^ChatCompletionContentPartInputAudio$InputAudio$Builder ab
        (ChatCompletionContentPartInputAudio$InputAudio/builder)
        ^ChatCompletionContentPartInputAudio$Builder b (ChatCompletionContentPartInputAudio/builder)]
    (when-not data (impl/missing-key! :data))
    (when-not format (impl/missing-key! :format))
    (.data ab ^String data)
    (.format ab (ChatCompletionContentPartInputAudio$InputAudio$Format/of (name format)))
    (.inputAudio b (.build ab))
    (.build b)))

(defn- ->chat-content-part ^ChatCompletionContentPart [{:keys [type] :as part}]
  (case (keyword type)
    :text (ChatCompletionContentPart/ofText (->chat-content-part-text part))
    :image (ChatCompletionContentPart/ofImageUrl (->chat-content-part-image part))
    :input-audio (ChatCompletionContentPart/ofInputAudio (->chat-content-part-input-audio part))
    (throw (ex-info (str "Unknown chat content type " type)
                    {:openai/error :unknown-content-type :type type}))))

(defn- ->chat-message-tool-call ^ChatCompletionMessageToolCall [{:keys [id type function]}]
  (case (keyword type)
    :function
    (let [{:keys [name arguments]} function
          ^ChatCompletionMessageFunctionToolCall$Function$Builder fb
          (ChatCompletionMessageFunctionToolCall$Function/builder)
          ^ChatCompletionMessageFunctionToolCall$Builder b
          (ChatCompletionMessageFunctionToolCall/builder)]
      (when-not id (impl/missing-key! :id))
      (when-not name (impl/missing-key! :name))
      (.name fb ^String name)
      (.arguments fb ^String (impl/encode-output arguments))
      (.id b ^String id)
      (.type b (JsonValue/from "function"))
      (.function b (.build fb))
      (ChatCompletionMessageToolCall/ofFunction (.build b)))
    (throw (ex-info (str "Unknown chat tool call type " type)
                    {:openai/error :unknown-tool-call-type :type type}))))

(defn- ->chat-system-message ^ChatCompletionMessageParam [{:keys [content]}]
  (let [^ChatCompletionSystemMessageParam$Builder b (ChatCompletionSystemMessageParam/builder)]
    (when-not content (impl/missing-key! :content))
    (if (string? content)
      (.content b ^String content)
      (.contentOfArrayOfContentParts b ^java.util.List (mapv ->chat-content-part-text content)))
    (ChatCompletionMessageParam/ofSystem (.build b))))

(defn- ->chat-developer-message ^ChatCompletionMessageParam [{:keys [content]}]
  (let [^ChatCompletionDeveloperMessageParam$Builder b (ChatCompletionDeveloperMessageParam/builder)]
    (when-not content (impl/missing-key! :content))
    (if (string? content)
      (.content b ^String content)
      (.contentOfArrayOfContentParts b ^java.util.List (mapv ->chat-content-part-text content)))
    (ChatCompletionMessageParam/ofDeveloper (.build b))))

(defn- ->chat-user-message ^ChatCompletionMessageParam [{:keys [content]}]
  (let [^ChatCompletionUserMessageParam$Builder b (ChatCompletionUserMessageParam/builder)]
    (when-not content (impl/missing-key! :content))
    (if (string? content)
      (.content b ^String content)
      (.contentOfArrayOfContentParts b ^java.util.List (mapv ->chat-content-part content)))
    (ChatCompletionMessageParam/ofUser (.build b))))

(defn- ->chat-assistant-message ^ChatCompletionMessageParam [{:keys [content tool-calls refusal]}]
  (let [^ChatCompletionAssistantMessageParam$Builder b (ChatCompletionAssistantMessageParam/builder)]
    (when content (.content b ^String content))
    (when refusal (.refusal b ^String refusal))
    (when (seq tool-calls) (.toolCalls b ^java.util.List (mapv ->chat-message-tool-call tool-calls)))
    (ChatCompletionMessageParam/ofAssistant (.build b))))

(defn- ->chat-tool-message ^ChatCompletionMessageParam [{:keys [tool-call-id content]}]
  (let [^ChatCompletionToolMessageParam$Builder b (ChatCompletionToolMessageParam/builder)]
    (when-not tool-call-id (impl/missing-key! :tool-call-id))
    (when-not content (impl/missing-key! :content))
    (.toolCallId b ^String tool-call-id)
    (.content b ^String content)
    (ChatCompletionMessageParam/ofTool (.build b))))

(defn- ->chat-message ^ChatCompletionMessageParam [{:keys [role] :as m}]
  (when-not role (impl/missing-key! :role))
  (case (keyword role)
    :system (->chat-system-message m)
    :developer (->chat-developer-message m)
    :user (->chat-user-message m)
    :assistant (->chat-assistant-message m)
    :tool (->chat-tool-message m)
    (throw (ex-info (str "Unknown chat message role " role)
                    {:openai/error :unknown-role :role role}))))

(defn- ->chat-function-parameters ^FunctionParameters [m]
  (let [^FunctionTool$Parameters rp (->function-parameters m)
        ^FunctionParameters$Builder b (FunctionParameters/builder)]
    (.additionalProperties b ^java.util.Map (._additionalProperties rp))
    (.build b)))

(defn- ->chat-function-definition ^FunctionDefinition [{:keys [name description parameters strict]}]
  (let [^FunctionDefinition$Builder b (FunctionDefinition/builder)]
    (when-not name (impl/missing-key! :name))
    (.name b ^String name)
    (when description (.description b ^String description))
    (when parameters (.parameters b (->chat-function-parameters parameters)))
    (when (some? strict) (.strict b (boolean strict)))
    (.build b)))

(defn- ->chat-function-tool ^ChatCompletionFunctionTool [tool]
  (let [^ChatCompletionFunctionTool$Builder b (ChatCompletionFunctionTool/builder)]
    (.type b (JsonValue/from "function"))
    (.function b (->chat-function-definition tool))
    (.build b)))

(defn- ->chat-tool ^ChatCompletionTool [{:keys [type] :as tool}]
  (case (keyword type)
    :function (ChatCompletionTool/ofFunction (->chat-function-tool tool))
    (throw (ex-info (str "Unknown chat tool type " type)
                    {:openai/error :unknown-tool-type :type type}))))

(defn- ->chat-tool-choice-option ^ChatCompletionToolChoiceOption [choice]
  (ChatCompletionToolChoiceOption/ofAuto (ChatCompletionToolChoiceOption$Auto/of (name choice))))

(defn- ->chat-named-tool-choice ^ChatCompletionNamedToolChoice [{:keys [name]}]
  (let [^ChatCompletionNamedToolChoice$Function$Builder fb
        (ChatCompletionNamedToolChoice$Function/builder)
        ^ChatCompletionNamedToolChoice$Builder b (ChatCompletionNamedToolChoice/builder)]
    (when-not name (impl/missing-key! :name))
    (.name fb ^String name)
    (.type b (JsonValue/from "function"))
    (.function b (.build fb))
    (.build b)))

(defn- ->chat-tool-choice ^ChatCompletionToolChoiceOption [choice]
  (if (map? choice)
    (case (keyword (:type choice))
      :function (ChatCompletionToolChoiceOption/ofNamedToolChoice (->chat-named-tool-choice choice))
      (throw (ex-info (str "Unknown chat tool choice type " (:type choice))
                      {:openai/error :unknown-tool-choice-type
                       :type (:type choice)})))
    (->chat-tool-choice-option choice)))

(defn- ->chat-response-format ^ChatCompletionCreateParams$ResponseFormat [{:keys [type json-schema]}]
  (case (keyword type)
    :json-object
    (ChatCompletionCreateParams$ResponseFormat/ofJsonObject
     (let [^ResponseFormatJsonObject$Builder b (ResponseFormatJsonObject/builder)]
       (.type b (JsonValue/from "json_object"))
       (.build b)))
    :json-schema
    (let [{:keys [name schema strict description]} json-schema
          ^ResponseFormatJsonSchema$JsonSchema$Schema$Builder sb
          (ResponseFormatJsonSchema$JsonSchema$Schema/builder)
          ^ResponseFormatJsonSchema$JsonSchema$Builder jsb
          (ResponseFormatJsonSchema$JsonSchema/builder)
          ^ResponseFormatJsonSchema$Builder rb
          (ResponseFormatJsonSchema/builder)]
      (when-not name (impl/missing-key! :name))
      (when-not schema (impl/missing-key! :schema))
      (.additionalProperties sb ^java.util.Map (impl/->json-schema-properties schema))
      (.name jsb ^String name)
      (.schema jsb (.build sb))
      (when description (.description jsb ^String description))
      (when (some? strict) (.strict jsb (boolean strict)))
      (.type rb (JsonValue/from "json_schema"))
      (.jsonSchema rb (.build jsb))
      (ChatCompletionCreateParams$ResponseFormat/ofJsonSchema (.build rb)))
    (throw (ex-info (str "Unknown chat response format " type)
                    {:openai/error :unknown-response-format :type type}))))

(defn- ->chat-stream-options ^ChatCompletionStreamOptions [{:keys [include-usage]}]
  (let [^ChatCompletionStreamOptions$Builder b (ChatCompletionStreamOptions/builder)]
    (when (some? include-usage) (.includeUsage b (boolean include-usage)))
    (.build b)))

(defn- ->chat-params ^ChatCompletionCreateParams
  [{:keys [model messages temperature top-p max-tokens max-completion-tokens n stop
           presence-penalty frequency-penalty logit-bias seed user metadata store
           service-tier parallel-tool-calls logprobs top-logprobs tools tool-choice
           response-format reasoning-effort stream-options]}]
  (when-not model (impl/missing-key! :model))
  (when-not messages (impl/missing-key! :messages))
  (let [^ChatCompletionCreateParams$Builder b (ChatCompletionCreateParams/builder)]
    (.model b ^String model)
    (.messages b ^java.util.List (mapv ->chat-message messages))
    (when temperature (.temperature b (double temperature)))
    (when top-p (.topP b (double top-p)))
    (when max-tokens (.maxTokens b (long max-tokens)))
    (when max-completion-tokens (.maxCompletionTokens b (long max-completion-tokens)))
    (when n (.n b (long n)))
    (when stop
      (if (string? stop)
        (.stop b ^String stop)
        (.stopOfStrings b ^java.util.List (vec stop))))
    (when presence-penalty (.presencePenalty b (double presence-penalty)))
    (when frequency-penalty (.frequencyPenalty b (double frequency-penalty)))
    (when logit-bias (.logitBias b (->chat-logit-bias logit-bias)))
    (when seed (.seed b (long seed)))
    (when user (.user b ^String user))
    (when metadata (.metadata b (->chat-metadata metadata)))
    (when (some? store) (.store b (boolean store)))
    (when service-tier (.serviceTier b (ChatCompletionCreateParams$ServiceTier/of (impl/enum-name service-tier))))
    (when (some? parallel-tool-calls) (.parallelToolCalls b (boolean parallel-tool-calls)))
    (when (some? logprobs) (.logprobs b (boolean logprobs)))
    (when top-logprobs (.topLogprobs b (long top-logprobs)))
    (when reasoning-effort (.reasoningEffort b (ReasoningEffort/of (name reasoning-effort))))
    (doseq [t tools] (.addTool b (->chat-tool t)))
    (when tool-choice (.toolChoice b (->chat-tool-choice tool-choice)))
    (when response-format (.responseFormat b (->chat-response-format response-format)))
    (when stream-options (.streamOptions b (->chat-stream-options stream-options)))
    (.build b)))

(defn- completion-usage->map [^CompletionUsage u]
  (cond-> {:prompt-tokens (.promptTokens u)
           :completion-tokens (.completionTokens u)
           :total-tokens (.totalTokens u)}
    (.isPresent (.computeUnits u)) (assoc :compute-units (.get (.computeUnits u)))))

(defn- chat-message-tool-call->map [^ChatCompletionMessageToolCall c]
  (cond
    (.isFunction c) (let [^ChatCompletionMessageFunctionToolCall f (.asFunction c)
                          ^ChatCompletionMessageFunctionToolCall$Function fnc (.function f)]
                      {:id (.id f)
                       :type :function
                       :function {:name (.name fnc)
                                  :arguments (impl/parse-arguments (.arguments fnc))}})
    :else {:type :unknown}))

(defn- chat-message->map [^ChatCompletionMessage m]
  (cond-> {:role (impl/->keyword (.asStringOrThrow (._role m)))}
    (.isPresent (.content m)) (assoc :content (.get (.content m)))
    (.isPresent (.toolCalls m)) (assoc :tool-calls (mapv chat-message-tool-call->map (.get (.toolCalls m))))
    (.isPresent (.refusal m)) (assoc :refusal (.get (.refusal m)))))

(defn- chat-choice->map [^ChatCompletion$Choice c]
  {:index (.index c)
   :finish-reason (impl/->keyword (.asString ^ChatCompletion$Choice$FinishReason (.finishReason c)))
   :message (chat-message->map (.message c))})

(defn- chat-output-text [choices]
  (or (some (fn [choice] (get-in choice [:message :content])) choices)
      ""))

(defn- chat-completion->map [^ChatCompletion r]
  (let [choices (mapv chat-choice->map (.choices r))]
    (cond-> {:id (.id r)
             :model (.model r)
             :created (.created r)
             :choices choices
             :text (chat-output-text choices)}
      (.isPresent (.usage r)) (assoc :usage (completion-usage->map (.get (.usage r))))
      (.isPresent (.serviceTier r)) (assoc :service-tier (impl/->keyword (.asString ^ChatCompletion$ServiceTier (.get (.serviceTier r))))))))

(defn- ->chat-completion-update-metadata ^ChatCompletionUpdateParams$Metadata [m]
  (let [^ChatCompletionUpdateParams$Metadata$Builder b (ChatCompletionUpdateParams$Metadata/builder)]
    (.additionalProperties b ^java.util.Map (impl/->json-value-properties m))
    (.build b)))

(defn- ->chat-completion-update-params ^ChatCompletionUpdateParams
  [^String completion-id {:keys [metadata]}]
  (let [^ChatCompletionUpdateParams$Builder b (ChatCompletionUpdateParams/builder)]
    (.completionId b completion-id)
    (when metadata (.metadata b (->chat-completion-update-metadata metadata)))
    (.build b)))

(defn- ->chat-completion-list-metadata ^ChatCompletionListParams$Metadata [m]
  (let [^ChatCompletionListParams$Metadata$Builder b (ChatCompletionListParams$Metadata/builder)]
    (doseq [[k v] m]
      (.putAdditionalProperty b (name k) (str v)))
    (.build b)))

(defn- ->chat-completion-list-params ^ChatCompletionListParams
  [{:keys [model metadata after limit order]}]
  (let [^ChatCompletionListParams$Builder b (ChatCompletionListParams/builder)]
    (when model (.model b ^String model))
    (when metadata (.metadata b (->chat-completion-list-metadata metadata)))
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (ChatCompletionListParams$Order/of (name order))))
    (.build b)))

(defn- ->chat-completion-message-list-params ^MessageListParams
  [^String completion-id {:keys [after limit order]}]
  (let [^MessageListParams$Builder b (MessageListParams/builder)]
    (.completionId b completion-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (MessageListParams$Order/of (name order))))
    (.build b)))

(defn- stored-chat-message->map [^ChatCompletionStoreMessage m]
  (assoc (chat-message->map (.toChatCompletionMessage m)) :id (.id m)))

(defn- deleted-chat-completion->map [^ChatCompletionDeleted c]
  {:id (.id c)
   :deleted (.deleted c)})

(defn- chat-delta-tool-call->map [^ChatCompletionChunk$Choice$Delta$ToolCall c]
  (cond-> {:index (.index c)}
    (.isPresent (.id c)) (assoc :id (.get (.id c)))
    (.isPresent (.type c)) (assoc :type (impl/->keyword (.asString ^ChatCompletionChunk$Choice$Delta$ToolCall$Type (.get (.type c)))))
    (.isPresent (.function c)) (assoc :function
                                      (let [^ChatCompletionChunk$Choice$Delta$ToolCall$Function f
                                            (.get (.function c))]
                                        (cond-> {}
                                          (.isPresent (.name f)) (assoc :name (.get (.name f)))
                                          (.isPresent (.arguments f)) (assoc :arguments (.get (.arguments f))))))))

(defn- chat-delta->map [^ChatCompletionChunk$Choice$Delta d]
  (cond-> {}
    (.isPresent (.role d)) (assoc :role (impl/->keyword (.asString ^ChatCompletionChunk$Choice$Delta$Role (.get (.role d)))))
    (.isPresent (.content d)) (assoc :content (.get (.content d)))
    (.isPresent (.toolCalls d)) (assoc :tool-calls (mapv chat-delta-tool-call->map (.get (.toolCalls d))))))

(defn- chat-chunk-choice->map [^ChatCompletionChunk$Choice c]
  (cond-> {:index (.index c)
           :delta (chat-delta->map (.delta c))}
    (.isPresent (.finishReason c)) (assoc :finish-reason (impl/->keyword (.asString ^ChatCompletionChunk$Choice$FinishReason (.get (.finishReason c)))))))

(defn- chat-chunk->map [^ChatCompletionChunk chunk]
  (cond-> {:type :chunk
           :choices (mapv chat-chunk-choice->map (.choices chunk))}
    (.isPresent (.usage chunk)) (assoc :usage (completion-usage->map (.get (.usage chunk))))))

(defn- drain-chat-stream
  ^String [^StreamResponse sr on-event]
  (let [sb (StringBuilder.)]
    (doseq [chunk (iterator-seq (.iterator (.stream sr)))]
      (let [m (chat-chunk->map chunk)]
        (doseq [choice (:choices m)]
          (when-let [content (get-in choice [:delta :content])]
            (.append sb ^String content)))
        (when on-event (on-event m))))
    (str sb)))

(defn create-chat-completion
  "Send a Chat Completions API request and return a Clojure map.

  Request keys: `:model` (required string), `:messages` (required vector),
  `:temperature`, `:top-p`, `:max-tokens`, `:max-completion-tokens`, `:n`,
  `:stop`, `:presence-penalty`, `:frequency-penalty`, `:logit-bias`, `:seed`,
  `:user`, `:metadata`, `:store`, `:service-tier`, `:parallel-tool-calls`,
  `:logprobs`, `:top-logprobs`, `:tools`, `:tool-choice`, `:response-format`,
  `:reasoning-effort`, and `:stream-options`.

  Message items accept `{:role :system|:developer|:user|:assistant|:tool
  :content \"...\"}`. User content may be a vector of text, image, or
  input-audio part maps. Assistant messages may include `:tool-calls`; tool
  messages require `:tool-call-id`.

  Returns `{:id :model :created :choices :usage :text}` plus `:service-tier`
  when present. This is the compatibility path for OpenAI-compatible endpoints
  that do not support the Responses API."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ChatService chat (.chat client)
          ^ChatCompletionService svc (.completions chat)]
      (chat-completion->map (.create svc (->chat-params req))))))

(defn get-chat-completion
  "Retrieve one stored chat completion by id."
  [^OpenAIClient client ^String completion-id]
  (impl/with-api-errors
    (let [^ChatService chat (.chat client)
          ^ChatCompletionService svc (.completions chat)]
      (chat-completion->map (.retrieve svc completion-id)))))

(defn update-chat-completion
  "Update metadata on a stored chat completion."
  [^OpenAIClient client ^String completion-id opts]
  (impl/with-api-errors
    (let [^ChatService chat (.chat client)
          ^ChatCompletionService svc (.completions chat)]
      (chat-completion->map (.update svc (->chat-completion-update-params completion-id opts))))))

(defn list-chat-completions
  "List stored chat completions. It follows each page automatically."
  ([^OpenAIClient client]
   (list-chat-completions client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^ChatService chat (.chat client)
           ^ChatCompletionService svc (.completions chat)
           ^ChatCompletionListPage p (.list svc (->chat-completion-list-params opts))]
       (mapv chat-completion->map (impl/all-pages p))))))

(defn delete-chat-completion
  "Delete a stored chat completion and return `{:id :deleted}`."
  [^OpenAIClient client ^String completion-id]
  (impl/with-api-errors
    (let [^ChatService chat (.chat client)
          ^ChatCompletionService svc (.completions chat)]
      (deleted-chat-completion->map (.delete svc completion-id)))))

(defn list-chat-completions-lazy
  "Lazy sibling of `list-chat-completions`; accepts optional `:max-items` and `:max-pages`."
  ([^OpenAIClient client] (list-chat-completions-lazy client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^ChatService chat (.chat client)
           ^ChatCompletionService svc (.completions chat)
           ^ChatCompletionListPage p (.list svc (->chat-completion-list-params opts))]
       (map chat-completion->map (impl/lazy-pages p opts))))))

(defn list-chat-completion-messages
  "List messages from a stored chat completion. It follows each page automatically."
  ([^OpenAIClient client ^String completion-id]
   (list-chat-completion-messages client completion-id {}))
  ([^OpenAIClient client ^String completion-id opts]
   (impl/with-api-errors
     (let [^ChatService chat (.chat client)
           ^ChatCompletionService completions (.completions chat)
           ^MessageService svc (.messages completions)
           ^MessageListPage p (.list svc (->chat-completion-message-list-params completion-id opts))]
       (mapv stored-chat-message->map (impl/all-pages p))))))

(defn stream-chat-completion
  "Stream a Chat Completions API request. It calls `on-event` with a normalized
  chunk map. It returns concatenated content deltas. It takes the same `req` map
  as `create-chat-completion`. It closes the HTTP stream automatically."
  ^String [^OpenAIClient client req on-event]
  (impl/with-api-errors
    (let [^ChatService chat (.chat client)
          ^ChatCompletionService svc (.completions chat)]
      (with-open [^StreamResponse sr (.createStreaming svc (->chat-params req))]
        (drain-chat-stream sr on-event)))))

(defn stream-chat-completion-text
  "Stream a Chat Completions API request. It calls `on-text` with each content
  delta. It returns the full concatenated text."
  ^String [^OpenAIClient client req on-text]
  (stream-chat-completion
   client req
   (fn [m]
     (doseq [choice (:choices m)]
       (when-let [content (get-in choice [:delta :content])]
         (when on-text (on-text content)))))))

(defn get-response
  "Retrieve one stored response by id as a response map. Pass `{:lossless? true}`
  as the optional third argument to include `:openai/raw`."
  ([^OpenAIClient client ^String response-id]
   (get-response client response-id {}))
  ([^OpenAIClient client ^String response-id opts]
   (impl/with-api-errors
     (let [^ResponseService svc (.responses client)]
       (response->map (.retrieve svc response-id) opts)))))

(defn- ->input-item-list-params ^InputItemListParams
  [^String response-id {:keys [after include limit order]}]
  (let [^InputItemListParams$Builder b (InputItemListParams/builder)]
    (.responseId b response-id)
    (when after (.after b ^String after))
    (doseq [i include]
      (.addInclude b (ResponseIncludable/of (impl/enum-name i))))
    (when limit (.limit b (long limit)))
    (when order (.order b (InputItemListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn list-input-items
  "List input items for a stored response id as normalized maps. It follows each
  page automatically. Optional keys are `:after`, `:include`, `:limit`, and
  `:order`."
  ([^OpenAIClient client ^String response-id]
   (list-input-items client response-id {}))
  ([^OpenAIClient client ^String response-id opts]
   (impl/with-api-errors
     (let [^ResponseService svc (.responses client)
           ^InputItemService items (.inputItems svc)
           ^InputItemListPage p (.list items (->input-item-list-params response-id opts))]
       (mapv response-item->map (impl/all-pages p))))))

(defn delete-response
  "Delete one stored response by id. The OpenAI Java SDK returns void."
  [^OpenAIClient client ^String response-id]
  (impl/with-api-errors
    (let [^ResponseService svc (.responses client)]
      (.delete svc response-id))
    nil))

(defn list-input-items-lazy
  "Lazy sibling of `list-input-items`; accepts optional `:max-items` and `:max-pages`."
  ([^OpenAIClient client ^String response-id]
   (list-input-items-lazy client response-id {}))
  ([^OpenAIClient client ^String response-id opts]
   (impl/with-api-errors
     (let [^ResponseService svc (.responses client)
           ^InputItemService items (.inputItems svc)
           ^InputItemListPage p (.list items (->input-item-list-params response-id opts))]
       (map response-item->map (impl/lazy-pages p opts))))))

(defn cancel-response
  "Cancel an in-progress response by id and return the response map. Pass
  `{:lossless? true}` as the optional third argument to include `:openai/raw`."
  ([^OpenAIClient client ^String response-id]
   (cancel-response client response-id {}))
  ([^OpenAIClient client ^String response-id opts]
   (impl/with-api-errors
     (let [^ResponseService svc (.responses client)]
       (response->map (.cancel svc response-id) opts)))))

(defn- compacted-response->map [^com.openai.models.responses.CompactedResponse r]
  (let [items (mapv output-item->map (.output r))]
    {:id (.id r)
     :output items
     :text (impl/output-text items)
     :usage (usage->map (.usage r))
     :created-at (.createdAt r)}))

(defn compact
  "Compact a previous response by id and return the compacted response map.
  Pass `{:lossless? true}` as the optional third argument to include
  `:openai/raw`."
  ([^OpenAIClient client ^String response-id]
   (compact client response-id {}))
  ([^OpenAIClient client ^String response-id opts]
   (impl/with-api-errors
     (let [^ResponseService svc (.responses client)
           r (.compact svc (-> (com.openai.models.responses.ResponseCompactParams/builder)
                               (.previousResponseId response-id)
                               (.build)))]
       (impl/preserve-raw (compacted-response->map r) r opts)))))

;; Files

(defn- ->file-purpose ^FilePurpose [purpose]
  (FilePurpose/of (impl/enum-name purpose)))

(defn- ->file-expires-after ^FileCreateParams$ExpiresAfter [{:keys [seconds]}]
  (when-not seconds (impl/missing-key! :seconds))
  (let [^FileCreateParams$ExpiresAfter$Builder b (FileCreateParams$ExpiresAfter/builder)]
    (.anchor b (JsonValue/from "created_at"))
    (.seconds b (long seconds))
    (.build b)))

(defn- ->file-input-stream ^java.io.InputStream [file]
  (cond
    (instance? java.io.InputStream file) file
    (bytes? file) (java.io.ByteArrayInputStream. ^bytes file)
    :else (throw (ex-info (str "Unsupported :file type " (class file))
                          {:openai/error :unsupported-file-type :class (class file)}))))

(defn- ->file-create-params ^FileCreateParams
  [{:keys [file purpose filename expires-after]}]
  (when-not file (impl/missing-key! :file))
  (when-not purpose (impl/missing-key! :purpose))
  (let [^FileCreateParams$Builder b (FileCreateParams/builder)]
    (cond
      (instance? java.nio.file.Path file) (.file b ^java.nio.file.Path file)
      (string? file) (.file b (.toPath (java.io.File. ^String file)))
      filename (.file b (-> (MultipartField/builder)
                            (.value (->file-input-stream file))
                            (.filename ^String filename)
                            (.build)))
      :else (.file b (->file-input-stream file)))
    (.purpose b (->file-purpose purpose))
    (when expires-after (.expiresAfter b (->file-expires-after expires-after)))
    (.build b)))

(defn- file->map [^FileObject f]
  (cond-> {:id (.id f)
           :bytes (.bytes f)
           :created-at (.createdAt f)
           :filename (.filename f)
           :purpose (impl/->keyword (.asString (.purpose f)))
           :status (impl/->keyword (.asString (.status f)))}
    (.isPresent (.expiresAt f)) (assoc :expires-at (.get (.expiresAt f)))
    (.isPresent (.statusDetails f)) (assoc :status-details (.get (.statusDetails f)))))

(defn- ->file-list-params ^FileListParams [{:keys [purpose order after limit]}]
  (let [^FileListParams$Builder b (FileListParams/builder)]
    (when purpose (.purpose b ^String (name purpose)))
    (when order (.order b (FileListParams$Order/of (name order))))
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (.build b)))

(defn upload-file
  "Upload a file. `:file` (required) is a `java.nio.file.Path`, a string path,
  a byte array, or an `InputStream`; `:purpose` (required) is e.g. `:batch`,
  `:assistants`, `:fine-tune`, `:vision`, `:user-data`, or `:evals`.
  Optional: `:filename` (used with byte-array/stream input) and
  `:expires-after {:seconds n}` (anchored to file creation time).

  Returns `{:id :bytes :created-at :filename :purpose :status}` plus
  `:expires-at`/`:status-details` when present."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^FileService svc (.files client)]
      (file->map (.create svc (->file-create-params req))))))

(defn get-file
  "Retrieve one file's metadata by id as a file map."
  [^OpenAIClient client ^String file-id]
  (impl/with-api-errors
    (let [^FileService svc (.files client)]
      (file->map (.retrieve svc file-id)))))

(defn file-content
  "Download a file's content by id and return it as a byte array."
  ^bytes [^OpenAIClient client ^String file-id]
  (impl/with-api-errors
    (let [^FileService svc (.files client)]
      (with-open [r (.content svc file-id)]
        (.readAllBytes (.body r))))))

(defn list-files
  "List files as a vector of file maps. Optional keys are `:purpose`, `:order`
  (`:asc`/`:desc`), `:after`, and `:limit`. It follows each page automatically."
  ([^OpenAIClient client] (list-files client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^FileService svc (.files client)
           ^FileListPage p (.list svc (->file-list-params opts))]
       (mapv file->map (impl/all-pages p))))))

(defn delete-file
  "Delete a file by id. Returns `{:id \"...\" :deleted true|false}`."
  [^OpenAIClient client ^String file-id]
  (impl/with-api-errors
    (let [^FileService svc (.files client)
          ^FileDeleted d (.delete svc file-id)]
      {:id (.id d) :deleted (.deleted d)})))

(defn list-files-lazy
  "Lazy sibling of `list-files`; accepts optional `:max-items` and `:max-pages`."
  ([^OpenAIClient client] (list-files-lazy client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^FileService svc (.files client)
           ^FileListPage p (.list svc (->file-list-params opts))]
       (map file->map (impl/lazy-pages p opts))))))

;; Batches

(defn- ->batch-metadata ^BatchCreateParams$Metadata [m]
  (let [^BatchCreateParams$Metadata$Builder b (BatchCreateParams$Metadata/builder)]
    (doseq [[k v] (walk/stringify-keys m)]
      (.putAdditionalProperty b ^String k (JsonValue/from (str v))))
    (.build b)))

(defn- ->output-expires-after ^BatchCreateParams$OutputExpiresAfter [{:keys [seconds]}]
  (when-not seconds (impl/missing-key! :seconds))
  (let [^BatchCreateParams$OutputExpiresAfter$Builder b
        (BatchCreateParams$OutputExpiresAfter/builder)]
    (.anchor b (JsonValue/from "created_at"))
    (.seconds b (long seconds))
    (.build b)))

(defn- ->batch-create-params ^BatchCreateParams
  [{:keys [input-file-id endpoint completion-window metadata output-expires-after]}]
  (when-not input-file-id (impl/missing-key! :input-file-id))
  (when-not endpoint (impl/missing-key! :endpoint))
  (let [^BatchCreateParams$Builder b (BatchCreateParams/builder)]
    (.inputFileId b ^String input-file-id)
    (.endpoint b (BatchCreateParams$Endpoint/of endpoint))
    (.completionWindow b (BatchCreateParams$CompletionWindow/of (or completion-window "24h")))
    (when metadata (.metadata b (->batch-metadata metadata)))
    (when output-expires-after
      (.outputExpiresAfter b (->output-expires-after output-expires-after)))
    (.build b)))

(defn- batch->map [^Batch b]
  (cond-> {:id (.id b)
           :status (impl/->keyword (.asString (.status b)))
           :endpoint (.endpoint b)
           :input-file-id (.inputFileId b)
           :completion-window (.completionWindow b)
           :created-at (.createdAt b)}
    (.isPresent (.outputFileId b)) (assoc :output-file-id (.get (.outputFileId b)))
    (.isPresent (.errorFileId b)) (assoc :error-file-id (.get (.errorFileId b)))
    (.isPresent (.model b)) (assoc :model (.get (.model b)))
    (.isPresent (.completedAt b)) (assoc :completed-at (.get (.completedAt b)))
    (.isPresent (.failedAt b)) (assoc :failed-at (.get (.failedAt b)))
    (.isPresent (.expiresAt b)) (assoc :expires-at (.get (.expiresAt b)))
    (.isPresent (.requestCounts b))
    (assoc :request-counts
           (let [^BatchRequestCounts c (.get (.requestCounts b))]
             {:completed (.completed c) :failed (.failed c) :total (.total c)}))))

(defn- ->batch-list-params ^BatchListParams [{:keys [after limit]}]
  (let [^BatchListParams$Builder b (BatchListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (.build b)))

(defn create-batch
  "Create a batch job. Required: `:input-file-id` (an uploaded `:batch`-purpose
  JSONL file) and `:endpoint` (the API path string the batch targets, e.g.
  \"/v1/responses\", \"/v1/chat/completions\", or \"/v1/embeddings\").
  Optional: `:completion-window` (defaults to \"24h\"), `:metadata`, and
  `:output-expires-after {:seconds n}`.

  Returns `{:id :status :endpoint :input-file-id :completion-window
  :created-at}` plus `:output-file-id`, `:error-file-id`, `:model`,
  `:completed-at`, `:failed-at`, `:expires-at`, or `:request-counts
  {:completed :failed :total}` when present."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^BatchService svc (.batches client)]
      (batch->map (.create svc (->batch-create-params req))))))

(defn get-batch
  "Retrieve one batch by id as a batch map."
  [^OpenAIClient client ^String batch-id]
  (impl/with-api-errors
    (let [^BatchService svc (.batches client)]
      (batch->map (.retrieve svc batch-id)))))

(defn cancel-batch
  "Cancel an in-progress batch by id and return the batch map."
  [^OpenAIClient client ^String batch-id]
  (impl/with-api-errors
    (let [^BatchService svc (.batches client)]
      (batch->map (.cancel svc batch-id)))))

(defn list-batches
  "List batches as a vector of batch maps. Optional keys are `:after` and
  `:limit`. It follows each page automatically."
  ([^OpenAIClient client] (list-batches client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^BatchService svc (.batches client)
           ^BatchListPage p (.list svc (->batch-list-params opts))]
       (mapv batch->map (impl/all-pages p))))))

(defn list-batches-lazy
  "Lazy sibling of `list-batches`; accepts optional `:max-items` and `:max-pages`."
  ([^OpenAIClient client] (list-batches-lazy client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^BatchService svc (.batches client)
           ^BatchListPage p (.list svc (->batch-list-params opts))]
       (map batch->map (impl/lazy-pages p opts))))))
