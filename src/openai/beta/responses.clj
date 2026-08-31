(ns openai.beta.responses
  "Clojure wrapper for the beta Responses API."
  (:refer-clojure :exclude [compact])
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.core.http StreamResponse)
           (com.openai.models.beta.responses BetaCompactedResponse
                                             BetaEasyInputMessage
                                             BetaEasyInputMessage$Role
                                             BetaEasyInputMessage$Phase
                                             BetaResponse
                                             BetaResponseIncludable
                                             BetaResponseInputContent
                                             BetaResponseInputItem
                                             BetaResponseInputItem$Message
                                             BetaResponseInputItem$Message$Builder
                                             BetaResponseInputItem$Message$Role
                                             BetaResponseStreamEvent
                                             BetaToolChoiceOptions
                                             ResponseCreateParams
                                             ResponseCreateParams$Beta
                                             ResponseCreateParams$Builder
                                             ResponseCreateParams$Metadata
                                             ResponseCreateParams$Metadata$Builder
                                             ResponseCreateParams$MultiAgent
                                             ResponseCreateParams$MultiAgent$Builder
                                             ResponseCreateParams$PromptCacheOptions
                                             ResponseCreateParams$PromptCacheOptions$Builder
                                             ResponseCreateParams$PromptCacheOptions$Mode
                                             ResponseCreateParams$PromptCacheOptions$Ttl
                                             ResponseCreateParams$Reasoning
                                             ResponseCreateParams$Reasoning$Builder
                                             ResponseCreateParams$Reasoning$Effort
                                             ResponseCreateParams$StreamOptions
                                             ResponseCreateParams$StreamOptions$Builder
                                             ResponseCreateParams$ToolChoice
                                             ResponseCreateParams$ServiceTier
                                             ResponseCreateParams$Truncation
                                             BetaToolChoiceFunction
                                             BetaToolChoiceFunction$Builder
                                             ResponseCancelParams
                                             ResponseDeleteParams
                                             ResponseRetrieveParams
                                             ResponseCompactParams
                                             ResponseCompactParams$ServiceTier
                                             ResponseRetrieveParams$Builder
                                             ResponseCancelParams$Builder
                                             ResponseDeleteParams$Builder
                                             ResponseCompactParams$Builder)
           (com.openai.models.beta.responses.inputitems InputItemListPage
                                                         InputItemListParams
                                                         InputItemListParams$Builder
                                                         InputItemListParams$Order)
           (com.openai.models.beta.responses.inputtokens InputTokenCountParams
                                                         InputTokenCountParams$Builder
                                                         InputTokenCountParams$Truncation
                                                         InputTokenCountResponse)
           (com.openai.services.blocking.beta ResponseService)
           (com.openai.services.blocking.beta.responses InputItemService InputTokenService)))

(set! *warn-on-reflection* true)

(defn- ->input-text ^com.openai.models.beta.responses.BetaResponseInputContent [{:keys [text]}]
  (when-not text (impl/missing-key! :text))
  (let [b (com.openai.models.beta.responses.BetaResponseInputText/builder)]
    (.text b ^String text)
    (BetaResponseInputContent/ofInputText (.build b))))

(defn- ->input-image ^com.openai.models.beta.responses.BetaResponseInputContent
  [{:keys [image-url file-id detail]}]
  (let [b (com.openai.models.beta.responses.BetaResponseInputImage/builder)]
    (when image-url (.imageUrl b ^String image-url))
    (when file-id (.fileId b ^String file-id))
    (when detail
      (.detail b (com.openai.models.beta.responses.BetaResponseInputImage$Detail/of
                  (impl/enum-name detail))))
    (BetaResponseInputContent/ofInputImage (.build b))))

(defn- ->input-file ^com.openai.models.beta.responses.BetaResponseInputContent
  [{:keys [file-id filename file-data file-url detail]}]
  (let [b (com.openai.models.beta.responses.BetaResponseInputFile/builder)]
    (when file-id (.fileId b ^String file-id))
    (when filename (.filename b ^String filename))
    (when file-data (.fileData b ^String file-data))
    (when file-url (.fileUrl b ^String file-url))
    (when detail
      (.detail b (com.openai.models.beta.responses.BetaResponseInputFile$Detail/of
                  (impl/enum-name detail))))
    (BetaResponseInputContent/ofInputFile (.build b))))

(defn- ->input-content ^com.openai.models.beta.responses.BetaResponseInputContent
  [{:keys [type] :as part}]
  (case (keyword type)
    :text (->input-text part)
    :image (->input-image part)
    :file (->input-file part)
    (throw (ex-info (str "Unknown content type " type)
                    {:openai/error :unknown-content-type :type type}))))

(defn- ->function-call-output ^BetaResponseInputItem
  [{:keys [call-id output name namespace]}]
  (when-not call-id (impl/missing-key! :call-id))
  (let [b (com.openai.models.beta.responses.BetaResponseInputItem$FunctionCallOutput/builder)]
    (.callId b ^String call-id)
    (.output b ^String (impl/encode-output output))
    (when name (.name b ^String name))
    (when namespace (.namespace b ^String namespace))
    (BetaResponseInputItem/ofFunctionCallOutput (.build b))))

(defn- ->computer-call-output ^BetaResponseInputItem
  [{:keys [call-id output acknowledged-safety-checks status id]}]
  (when-not call-id (impl/missing-key! :call-id))
  (when-not output (impl/missing-key! :output))
  (let [b (com.openai.models.beta.responses.BetaResponseInputItem$ComputerCallOutput/builder)
        screenshot (com.openai.models.beta.responses.BetaResponseComputerToolCallOutputScreenshot/builder)]
    (.callId b ^String call-id)
    (when id (.id b ^String id))
    (when-let [file-id (:file-id output)] (.fileId screenshot ^String file-id))
    (when-let [image-url (:image-url output)] (.imageUrl screenshot ^String image-url))
    (.output b (.build screenshot))
    (doseq [{:keys [id code message]} acknowledged-safety-checks]
      (when-not id (impl/missing-key! :id))
      (let [check (com.openai.models.beta.responses.BetaResponseInputItem$ComputerCallOutput$AcknowledgedSafetyCheck/builder)]
        (.id check ^String id)
        (when code (.code check ^String code))
        (when message (.message check ^String message))
        (.addAcknowledgedSafetyCheck b (.build check))))
    (when status
      (.status b (com.openai.models.beta.responses.BetaResponseInputItem$ComputerCallOutput$Status/of
                  (impl/enum-name status))))
    (BetaResponseInputItem/ofComputerCallOutput (.build b))))

(defn- ->local-shell-call-output ^BetaResponseInputItem [{:keys [id output status]}]
  (when-not id (impl/missing-key! :id))
  (when-not (some? output) (impl/missing-key! :output))
  (let [b (com.openai.models.beta.responses.BetaResponseInputItem$LocalShellCallOutput/builder)]
    (.id b ^String id)
    (.output b ^String (impl/encode-output output))
    (when status
      (.status b (com.openai.models.beta.responses.BetaResponseInputItem$LocalShellCallOutput$Status/of
                  (impl/enum-name status))))
    (BetaResponseInputItem/ofLocalShellCallOutput (.build b))))

(defn- ->shell-output-content [{:keys [stdout stderr exit-code outcome]}]
  (let [b (com.openai.models.beta.responses.BetaResponseFunctionShellCallOutputContent/builder)]
    (.stdout b ^String (str (or stdout "")))
    (.stderr b ^String (str (or stderr "")))
    (if (= :timeout outcome)
      (.outcomeTimeout b)
      (.exitOutcome b (long (or exit-code 0))))
    (.build b)))

(defn- ->shell-call-output ^BetaResponseInputItem
  [{:keys [call-id output id status max-output-length]}]
  (when-not call-id (impl/missing-key! :call-id))
  (when-not output (impl/missing-key! :output))
  (let [b (com.openai.models.beta.responses.BetaResponseInputItem$ShellCallOutput/builder)]
    (.callId b ^String call-id)
    (.output b ^java.util.List (mapv ->shell-output-content output))
    (when id (.id b ^String id))
    (when max-output-length (.maxOutputLength b (long max-output-length)))
    (when status
      (.status b (com.openai.models.beta.responses.BetaResponseInputItem$ShellCallOutput$Status/of
                  (impl/enum-name status))))
    (BetaResponseInputItem/ofShellCallOutput (.build b))))

(defn- ->custom-tool-call-output ^BetaResponseInputItem
  [{:keys [call-id output id]}]
  (when-not call-id (impl/missing-key! :call-id))
  (when-not (some? output) (impl/missing-key! :output))
  (let [b (com.openai.models.beta.responses.BetaResponseCustomToolCallOutput/builder)]
    (.callId b ^String call-id)
    (.output b ^String (impl/encode-output output))
    (when id (.id b ^String id))
    (BetaResponseInputItem/ofCustomToolCallOutput (.build b))))

(declare ->tool)

(defn- ->tool-search-output ^BetaResponseInputItem
  [{:keys [tools call-id id execution status]}]
  (when-not tools (impl/missing-key! :tools))
  (let [b (com.openai.models.beta.responses.BetaResponseToolSearchOutputItemParam/builder)]
    (.tools b ^java.util.List (mapv ->tool tools))
    (when call-id (.callId b ^String call-id))
    (when id (.id b ^String id))
    (when execution
      (.execution b (com.openai.models.beta.responses.BetaResponseToolSearchOutputItemParam$Execution/of
                    (impl/enum-name execution))))
    (when status
      (.status b (com.openai.models.beta.responses.BetaResponseToolSearchOutputItemParam$Status/of
                  (impl/enum-name status))))
    (BetaResponseInputItem/ofToolSearchOutput (.build b))))

(defn- ->mcp-approval-response ^BetaResponseInputItem
  [{:keys [approval-request-id approve reason id]}]
  (when-not approval-request-id (impl/missing-key! :approval-request-id))
  (when-not (some? approve) (impl/missing-key! :approve))
  (let [b (com.openai.models.beta.responses.BetaResponseInputItem$McpApprovalResponse/builder)]
    (.approvalRequestId b ^String approval-request-id)
    (.approve b (boolean approve))
    (when reason (.reason b ^String reason))
    (when id (.id b ^String id))
    (BetaResponseInputItem/ofMcpApprovalResponse (.build b))))

(defn- ->agent-message ^BetaResponseInputItem [{:keys [author content recipient id]}]
  (when-not content (impl/missing-key! :content))
  (let [b (com.openai.models.beta.responses.BetaResponseInputItem$AgentMessage/builder)]
    (when author (.author b ^String author))
    (when recipient (.recipient b ^String recipient))
    (when id (.id b ^String id))
    (if (string? content)
      (.addInputTextContent b ^String content)
      (doseq [{:keys [text]} content] (.addInputTextContent b ^String text)))
    (BetaResponseInputItem/ofAgentMessage (.build b))))

(defn- ->beta-easy-input-message ^BetaResponseInputItem
  [{:keys [role content phase]}]
  (when-not role (impl/missing-key! :role))
  (when-not content (impl/missing-key! :content))
  (let [b (BetaEasyInputMessage/builder)]
    (.role b (BetaEasyInputMessage$Role/of (name role)))
    (if (string? content)
      (.content b ^String content)
      (.contentOfBetaResponseInputMessageContentList b
        ^java.util.List (mapv ->input-content content)))
    (when phase
      (.phase b (BetaEasyInputMessage$Phase/of (impl/enum-name phase))))
    (BetaResponseInputItem/ofBetaEasyInputMessage (.build b))))

(defn- ->beta-response-output-message ^BetaResponseInputItem
  [{:keys [id role status content]}]
  (when-not content (impl/missing-key! :content))
  (let [b (com.openai.models.beta.responses.BetaResponseOutputMessage/builder)
        ^com.openai.models.beta.responses.BetaResponseOutputText$Builder text
        (com.openai.models.beta.responses.BetaResponseOutputText/builder)]
    (when id (.id b ^String id))
    (when role (.role b (JsonValue/from (name role))))
    (when status
      (.status b (com.openai.models.beta.responses.BetaResponseOutputMessage$Status/of
                  (impl/enum-name status))))
    (.annotations text ^java.util.List (java.util.ArrayList.))
    (.text text ^String content)
    (.addContent b (.build text))
    (BetaResponseInputItem/ofBetaResponseOutputMessage (.build b))))

(defn- ->multi-agent-call ^BetaResponseInputItem
  [{:keys [action arguments call-id id]}]
  (when-not action (impl/missing-key! :action))
  (when-not call-id (impl/missing-key! :call-id))
  (let [b (com.openai.models.beta.responses.BetaResponseInputItem$MultiAgentCall/builder)]
    (.action b (com.openai.models.beta.responses.BetaResponseInputItem$MultiAgentCall$Action/of
                (impl/enum-name action)))
    (.callId b ^String call-id)
    (when arguments (.arguments b ^String (impl/encode-output arguments)))
    (when id (.id b ^String id))
    (BetaResponseInputItem/ofMultiAgentCall (.build b))))

(defn- ->multi-agent-call-output ^BetaResponseInputItem
  [{:keys [action call-id output id]}]
  (when-not action (impl/missing-key! :action))
  (when-not call-id (impl/missing-key! :call-id))
  (let [b (com.openai.models.beta.responses.BetaResponseInputItem$MultiAgentCallOutput/builder)]
    (.action b (com.openai.models.beta.responses.BetaResponseInputItem$MultiAgentCallOutput$Action/of
                (impl/enum-name action)))
    (.callId b ^String call-id)
    (when output
      (.output b ^java.util.List
               (mapv (fn [text]
                       (let [ob (com.openai.models.beta.responses.BetaResponseInputItem$MultiAgentCallOutput$Output/builder)]
                         (.text ob ^String text)
                         (.build ob)))
                     (if (sequential? output) output [output]))))
    (when id (.id b ^String id))
    (BetaResponseInputItem/ofMultiAgentCallOutput (.build b))))

(defn- ->message-input-item ^BetaResponseInputItem [{:keys [role content]}]
  (when-not role (impl/missing-key! :role))
  (when-not content (impl/missing-key! :content))
  (let [^BetaResponseInputItem$Message$Builder b (BetaResponseInputItem$Message/builder)]
    (.role b (BetaResponseInputItem$Message$Role/of (name role)))
    (if (string? content)
      (.addInputTextContent b ^String content)
      (.content b ^java.util.List (mapv ->input-content content)))
    (BetaResponseInputItem/ofMessage (.build b))))

(defn- ->input-item ^BetaResponseInputItem [{:keys [type] :as item}]
  (case (keyword type)
    :additional-tools (impl/sdk-input-object item BetaResponseInputItem)
    :apply-patch-call (impl/sdk-input-object item BetaResponseInputItem)
    :apply-patch-call-output (impl/sdk-input-object item BetaResponseInputItem)
    :code-interpreter-call (impl/sdk-input-object item BetaResponseInputItem)
    :compaction (impl/sdk-input-object item BetaResponseInputItem)
    :compaction-trigger (impl/sdk-input-object item BetaResponseInputItem)
    :computer-call (impl/sdk-input-object item BetaResponseInputItem)
    :function-call-output (->function-call-output item)
    :function-call (impl/sdk-input-object item BetaResponseInputItem)
    :file-search-call (impl/sdk-input-object item BetaResponseInputItem)
    :image-generation-call (impl/sdk-input-object item BetaResponseInputItem)
    :item-reference (impl/sdk-input-object item BetaResponseInputItem)
    :local-shell-call (impl/sdk-input-object item BetaResponseInputItem)
    :computer-call-output (->computer-call-output item)
    :local-shell-call-output (->local-shell-call-output item)
    :mcp-approval-request (impl/sdk-input-object item BetaResponseInputItem)
    :shell-call-output (->shell-call-output item)
    :custom-tool-call-output (->custom-tool-call-output item)
    :custom-tool-call (impl/sdk-input-object item BetaResponseInputItem)
    :shell-call (impl/sdk-input-object item BetaResponseInputItem)
    :mcp-approval-response (->mcp-approval-response item)
    :mcp-call (impl/sdk-input-object item BetaResponseInputItem)
    :mcp-list-tools (impl/sdk-input-object item BetaResponseInputItem)
    :program (impl/sdk-input-object item BetaResponseInputItem)
    :program-output (impl/sdk-input-object item BetaResponseInputItem)
    :reasoning (impl/sdk-input-object item BetaResponseInputItem)
    :tool-search-call (impl/sdk-input-object item BetaResponseInputItem)
    :tool-search-output (->tool-search-output item)
    :web-search-call (impl/sdk-input-object item BetaResponseInputItem)
    :agent-message (->agent-message item)
    :beta-easy-input-message (->beta-easy-input-message item)
    :beta-response-output-message (->beta-response-output-message item)
    :multi-agent-call (->multi-agent-call item)
    :multi-agent-call-output (->multi-agent-call-output item)
    :message (if (:id item)
               (impl/sdk-input-object item BetaResponseInputItem)
               (->message-input-item item))
    nil (->message-input-item item)
    (throw (ex-info (str "Unknown beta input type " type)
                    {:openai/error :unknown-input-type :type type}))))

(defn- ->prompt ^com.openai.models.beta.responses.BetaResponsePrompt
  [{:keys [id version variables]}]
  (let [b (com.openai.models.beta.responses.BetaResponsePrompt/builder)]
    (when-not id (impl/missing-key! :id))
    (.id b ^String id)
    (when version (.version b ^String version))
    (when variables
      (let [vb (com.openai.models.beta.responses.BetaResponsePrompt$Variables/builder)]
        (.additionalProperties vb ^java.util.Map (impl/->json-value-properties variables))
        (.variables b (.build vb))))
    (.build b)))

(defn- ->context-management
  [{:keys [type compact-threshold]}]
  (let [b (com.openai.models.beta.responses.ResponseCreateParams$ContextManagement/builder)]
    (when-not type (impl/missing-key! :type))
    (.type b ^String (impl/enum-name type))
    (when (some? compact-threshold) (.compactThreshold b (long compact-threshold)))
    (.build b)))

(defn- ->text-config ^com.openai.models.beta.responses.BetaResponseTextConfig
  [json-schema verbosity]
  (let [tb (com.openai.models.beta.responses.BetaResponseTextConfig/builder)]
    (when json-schema
      (let [{:keys [name schema strict description]} json-schema
            sb (com.openai.models.beta.responses.BetaResponseFormatTextJsonSchemaConfig$Schema/builder)
            fb (com.openai.models.beta.responses.BetaResponseFormatTextJsonSchemaConfig/builder)]
        (when-not name (impl/missing-key! :name))
        (when-not schema (impl/missing-key! :schema))
        (.additionalProperties sb ^java.util.Map (impl/->json-schema-properties schema))
        (.name fb ^String name)
        (.schema fb (.build sb))
        (when description (.description fb ^String description))
        (when (some? strict) (.strict fb (boolean strict)))
        (.format tb (.build fb))))
    (when verbosity
      (.verbosity tb (com.openai.models.beta.responses.BetaResponseTextConfig$Verbosity/of
                     (name verbosity))))
    (.build tb)))

(defn- ->moderation ^com.openai.models.beta.responses.ResponseCreateParams$Moderation [{:keys [model]}]
  (let [b (com.openai.models.beta.responses.ResponseCreateParams$Moderation/builder)]
    (when model (.model b ^String model))
    (.build b)))

(defn- ->function-parameters ^com.openai.models.beta.responses.BetaFunctionTool$Parameters [m]
  (let [b (com.openai.models.beta.responses.BetaFunctionTool$Parameters/builder)]
    (.additionalProperties b ^java.util.Map (impl/->json-value-properties m))
    (.build b)))

(defn- ->function-tool ^com.openai.models.beta.responses.BetaFunctionTool
  [{:keys [name description parameters strict]}]
  (when-not name (impl/missing-key! :name))
  (let [b (com.openai.models.beta.responses.BetaFunctionTool/builder)]
    (.name b ^String name)
    (when description (.description b ^String description))
    (.parameters b (->function-parameters (or parameters {})))
    (.strict b (boolean strict))
    (.build b)))

(defn- ->comparison-filter [{:keys [type key value]}]
  (when-not type (impl/missing-key! :type))
  (when-not key (impl/missing-key! :key))
  (let [b (com.openai.models.beta.responses.BetaFileSearchTool$Filters$ComparisonFilter/builder)]
    (.type b (com.openai.models.beta.responses.BetaFileSearchTool$Filters$ComparisonFilter$Type/of
              (impl/enum-name type)))
    (.key b ^String key)
    (cond
      (string? value) (.value b ^String value)
      (number? value) (.value b (double value))
      (instance? Boolean value) (.value b (boolean value))
      :else (.value b ^String (str value)))
    (.build b)))

(defn- ->file-search-filters ^com.openai.models.beta.responses.BetaFileSearchTool$Filters [filters]
  (com.openai.models.beta.responses.BetaFileSearchTool$Filters/ofComparisonFilter
   (->comparison-filter filters)))

(defn- ->ranking-options ^com.openai.models.beta.responses.BetaFileSearchTool$RankingOptions
  [{:keys [ranker score-threshold]}]
  (let [b (com.openai.models.beta.responses.BetaFileSearchTool$RankingOptions/builder)]
    (when ranker
      (.ranker b (com.openai.models.beta.responses.BetaFileSearchTool$RankingOptions$Ranker/of
                  (str ranker))))
    (when (some? score-threshold) (.scoreThreshold b (double score-threshold)))
    (.build b)))

(defn- ->file-search-tool [{:keys [vector-store-ids max-num-results filters ranking-options]}]
  (when-not (seq vector-store-ids) (impl/missing-key! :vector-store-ids))
  (let [b (com.openai.models.beta.responses.BetaFileSearchTool/builder)]
    (.vectorStoreIds b ^java.util.List (vec vector-store-ids))
    (when max-num-results (.maxNumResults b (long max-num-results)))
    (when filters (.filters b (->file-search-filters filters)))
    (when ranking-options (.rankingOptions b (->ranking-options ranking-options)))
    (.build b)))

(defn- ->web-search-tool [{:keys [search-context-size user-location allowed-domains] :as tool}]
  (let [b (com.openai.models.beta.responses.BetaWebSearchTool/builder)]
    (.type b com.openai.models.beta.responses.BetaWebSearchTool$Type/WEB_SEARCH)
    (when search-context-size
      (.searchContextSize b (com.openai.models.beta.responses.BetaWebSearchTool$SearchContextSize/of
                             (name search-context-size))))
    (when (contains? tool :allowed-domains)
      (if (seq allowed-domains)
        (let [fb (com.openai.models.beta.responses.BetaWebSearchTool$Filters/builder)]
          (.allowedDomains fb ^java.util.List (vec allowed-domains))
          (.filters b (.build fb)))
        (throw (ex-info "Explicitly empty :allowed-domains is not supported; omit the key to allow all domains."
                        {:openai/error :empty-allow-list :option :allowed-domains}))))
    (when user-location
      (let [location user-location
            ^com.openai.models.beta.responses.BetaWebSearchTool$UserLocation$Builder lb
            (com.openai.models.beta.responses.BetaWebSearchTool$UserLocation/builder)]
        (when-let [value (:city location)] (.city lb ^String value))
        (when-let [value (:country location)] (.country lb ^String value))
        (when-let [value (:region location)] (.region lb ^String value))
        (when-let [value (:timezone location)] (.timezone lb ^String value))
        (.userLocation b (.build lb))))
    (.build b)))

(defn- ->code-interpreter-tool [{:keys [container]}]
  (let [b (com.openai.models.beta.responses.BetaTool$CodeInterpreter/builder)]
    (if container
      (.container b ^String container)
      (.container b "auto"))
    (.build b)))

(defn- ->image-generation-tool
  [{:keys [action background input-fidelity model moderation output-compression
           output-format partial-images quality size]}]
  (let [b (com.openai.models.beta.responses.BetaTool$ImageGeneration/builder)]
    (when action
      (.action b (com.openai.models.beta.responses.BetaTool$ImageGeneration$Action/of
                  (impl/enum-name action))))
    (when background
      (.background b (com.openai.models.beta.responses.BetaTool$ImageGeneration$Background/of
                     (impl/enum-name background))))
    (when input-fidelity
      (.inputFidelity b (com.openai.models.beta.responses.BetaTool$ImageGeneration$InputFidelity/of
                        (impl/enum-name input-fidelity))))
    (when model (.model b ^String model))
    (when moderation
      (.moderation b (com.openai.models.beta.responses.BetaTool$ImageGeneration$Moderation/of
                     (impl/enum-name moderation))))
    (when output-compression (.outputCompression b (long output-compression)))
    (when output-format
      (.outputFormat b (com.openai.models.beta.responses.BetaTool$ImageGeneration$OutputFormat/of
                       (impl/enum-name output-format))))
    (when partial-images (.partialImages b (long partial-images)))
    (when quality
      (.quality b (com.openai.models.beta.responses.BetaTool$ImageGeneration$Quality/of
                  (impl/enum-name quality))))
    (when size (.size b ^String size))
    (.build b)))

(defn- ->mcp-headers ^com.openai.models.beta.responses.BetaTool$Mcp$Headers [headers]
  (let [b (com.openai.models.beta.responses.BetaTool$Mcp$Headers/builder)]
    (.additionalProperties b ^java.util.Map (impl/->json-value-properties headers))
    (.build b)))

(defn- ->mcp-tool [{:keys [server-label server-url allowed-tools require-approval headers] :as tool}]
  (when-not server-label (impl/missing-key! :server-label))
  (let [b (com.openai.models.beta.responses.BetaTool$Mcp/builder)]
    (.serverLabel b ^String server-label)
    (when server-url (.serverUrl b ^String server-url))
    (when (contains? tool :allowed-tools)
      (if (seq allowed-tools)
        (.allowedToolsOfMcp b ^java.util.List (vec allowed-tools))
        (throw (ex-info "Explicitly empty :allowed-tools is not supported; omit the key to allow all tools."
                        {:openai/error :empty-allow-list :option :allowed-tools}))))
    (when require-approval
      (.requireApproval b
                        (com.openai.models.beta.responses.BetaTool$Mcp$RequireApproval$McpToolApprovalSetting/of
                         (impl/enum-name require-approval))))
    (when headers (.headers b (->mcp-headers headers)))
    (.build b)))

(defn- ->shell-tool [{:keys [environment container-id]}]
  (let [b (com.openai.models.beta.responses.BetaFunctionShellTool/builder)]
    (case (keyword environment)
      :local (.environment b (.build (com.openai.models.beta.responses.BetaLocalEnvironment/builder)))
      :container-auto (.environment b (.build (com.openai.models.beta.responses.BetaContainerAuto/builder)))
      nil nil
      (throw (ex-info (str "Unknown beta shell environment " environment)
                      {:openai/error :unknown-shell-environment :environment environment})))
    (when container-id (.containerReferenceEnvironment b ^String container-id))
    (.build b)))

(defn- ->custom-tool [{:keys [name description format]}]
  (when-not name (impl/missing-key! :name))
  (let [b (com.openai.models.beta.responses.BetaCustomTool/builder)]
    (.name b ^String name)
    (when description (.description b ^String description))
    (when (= :text (keyword format)) (.formatText b))
    (.build b)))

(defn- ->tool-search-tool [{:keys [description execution parameters]}]
  (when-not parameters (impl/missing-key! :parameters))
  (let [b (com.openai.models.beta.responses.BetaToolSearchTool/builder)]
    (.parameters b (JsonValue/from (walk/stringify-keys parameters)))
    (when description (.description b ^String description))
    (when execution
      (.execution b (com.openai.models.beta.responses.BetaToolSearchTool$Execution/of
                     (impl/enum-name execution))))
    (.build b)))

(defn- ->tool ^com.openai.models.beta.responses.BetaTool [{:keys [type] :as tool}]
  (case (keyword type)
    :function (com.openai.models.beta.responses.BetaTool/ofFunction (->function-tool tool))
    :web-search (com.openai.models.beta.responses.BetaTool/ofWebSearch (->web-search-tool tool))
    :file-search (com.openai.models.beta.responses.BetaTool/ofFileSearch (->file-search-tool tool))
    :mcp (com.openai.models.beta.responses.BetaTool/ofMcp (->mcp-tool tool))
    :code-interpreter (com.openai.models.beta.responses.BetaTool/ofCodeInterpreter
                       (->code-interpreter-tool tool))
    :programmatic-tool-calling (com.openai.models.beta.responses.BetaTool/ofProgrammaticToolCalling)
    :image-generation (com.openai.models.beta.responses.BetaTool/ofImageGeneration
                       (->image-generation-tool tool))
    :computer (com.openai.models.beta.responses.BetaTool/ofComputer
               (.build (com.openai.models.beta.responses.BetaComputerTool/builder)))
    :local-shell (com.openai.models.beta.responses.BetaTool/ofLocalShell)
    :shell (com.openai.models.beta.responses.BetaTool/ofShell (->shell-tool tool))
    :apply-patch (com.openai.models.beta.responses.BetaTool/ofApplyPatch
                  (.build (com.openai.models.beta.responses.BetaApplyPatchTool/builder)))
    :custom (com.openai.models.beta.responses.BetaTool/ofCustom (->custom-tool tool))
    :tool-search (com.openai.models.beta.responses.BetaTool/ofToolSearch (->tool-search-tool tool))
    (throw (ex-info (str "Unknown beta tool type " type)
                    {:openai/error :unknown-tool-type :type type}))))

(defn- ->metadata ^ResponseCreateParams$Metadata [m]
  (let [^ResponseCreateParams$Metadata$Builder b (ResponseCreateParams$Metadata/builder)]
    (.additionalProperties b ^java.util.Map (impl/->json-value-properties m))
    (.build b)))

(defn- ->multi-agent ^ResponseCreateParams$MultiAgent [{:keys [enabled max-concurrent-subagents]}]
  (let [^ResponseCreateParams$MultiAgent$Builder b (ResponseCreateParams$MultiAgent/builder)]
    (when (some? enabled) (.enabled b (boolean enabled)))
    (when (some? max-concurrent-subagents)
      (.maxConcurrentSubagents b (long max-concurrent-subagents)))
    (.build b)))

(defn- ->reasoning ^ResponseCreateParams$Reasoning [{:keys [effort mode]}]
  (let [^ResponseCreateParams$Reasoning$Builder b (ResponseCreateParams$Reasoning/builder)]
    (when effort (.effort b (ResponseCreateParams$Reasoning$Effort/of (name effort))))
    (when mode (.mode b (name mode)))
    (.build b)))

(defn- ->prompt-cache-options ^ResponseCreateParams$PromptCacheOptions [{:keys [mode ttl]}]
  (let [^ResponseCreateParams$PromptCacheOptions$Builder b
        (ResponseCreateParams$PromptCacheOptions/builder)]
    (when mode (.mode b (ResponseCreateParams$PromptCacheOptions$Mode/of (name mode))))
    (when ttl (.ttl b (ResponseCreateParams$PromptCacheOptions$Ttl/of (name ttl))))
    (.build b)))

(defn- ->stream-options ^ResponseCreateParams$StreamOptions [{:keys [include-obfuscation]}]
  (let [^ResponseCreateParams$StreamOptions$Builder b (ResponseCreateParams$StreamOptions/builder)]
    (when (some? include-obfuscation) (.includeObfuscation b (boolean include-obfuscation)))
    (.build b)))

(defn- ->tool-choice ^ResponseCreateParams$ToolChoice [choice]
  (if (map? choice)
    (case (keyword (:type choice))
      :function
      (let [^BetaToolChoiceFunction$Builder b (BetaToolChoiceFunction/builder)]
        (.name b ^String (:name choice))
        (ResponseCreateParams$ToolChoice/ofBetaToolChoiceFunction (.build b)))
      :programmatic-tool-calling
      (ResponseCreateParams$ToolChoice/ofBetaSpecificProgrammaticToolCallingParam)
      (throw (ex-info (str "Unknown beta tool choice type " (:type choice))
                      {:openai/error :unknown-tool-choice-type :type (:type choice)})))
    (case (keyword choice)
      :auto (ResponseCreateParams$ToolChoice/ofBetaToolChoiceOptions BetaToolChoiceOptions/AUTO)
      :required (ResponseCreateParams$ToolChoice/ofBetaToolChoiceOptions BetaToolChoiceOptions/REQUIRED)
      :none (ResponseCreateParams$ToolChoice/ofBetaToolChoiceOptions BetaToolChoiceOptions/NONE)
      :programmatic-tool-calling (ResponseCreateParams$ToolChoice/ofBetaSpecificProgrammaticToolCallingParam)
      (throw (ex-info (str "Unknown beta tool choice " choice)
                      {:openai/error :unknown-tool-choice :tool-choice choice})))))

(defn- ->params ^ResponseCreateParams
  [{:keys [model input instructions max-output-tokens temperature top-p metadata previous-response-id
           store reasoning user tool-choice parallel-tool-calls background include truncation
           prompt-cache-key prompt-cache-options safety-identifier service-tier max-tool-calls
           top-logprobs stream-options betas multi-agent tools json-schema verbosity conversation
           moderation prompt context-management prompt-cache-retention]}]
  (when-not model (impl/missing-key! :model))
  (when-not input (impl/missing-key! :input))
  (let [^ResponseCreateParams$Builder b (ResponseCreateParams/builder)]
    (.model b ^String model)
    (if (string? input)
      (.input b ^String input)
      (.inputOfBetaResponse b ^java.util.List (mapv ->input-item input)))
    (doseq [beta betas]
      (.addBeta b (ResponseCreateParams$Beta/of (impl/enum-name beta))))
    (when instructions (.instructions b ^String instructions))
    (when max-output-tokens (.maxOutputTokens b (long max-output-tokens)))
    (when max-tool-calls (.maxToolCalls b (long max-tool-calls)))
    (when temperature (.temperature b (double temperature)))
    (when top-p (.topP b (double top-p)))
    (when top-logprobs (.topLogprobs b (long top-logprobs)))
    (when (some? background) (.background b (boolean background)))
    (doseq [i include] (.addInclude b (BetaResponseIncludable/of (impl/enum-name i))))
    (when truncation (.truncation b (ResponseCreateParams$Truncation/of (impl/enum-name truncation))))
    (when prompt-cache-key (.promptCacheKey b ^String prompt-cache-key))
    (when prompt-cache-options (.promptCacheOptions b (->prompt-cache-options prompt-cache-options)))
    (when prompt (.prompt b (->prompt prompt)))
    (doseq [context context-management]
      (.addContextManagement b (->context-management context)))
    (when prompt-cache-retention
      (.promptCacheRetention b
                             (com.openai.models.beta.responses.ResponseCreateParams$PromptCacheRetention/of
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
    (doseq [tool tools] (.addTool b (->tool tool)))
    (when tool-choice
      (if (= :programmatic-tool-calling (keyword tool-choice))
        (.toolChoiceBetaSpecificProgrammaticToolCallingParam b)
        (.toolChoice b (->tool-choice tool-choice))))
    (when (some? parallel-tool-calls) (.parallelToolCalls b (boolean parallel-tool-calls)))
    (when stream-options (.streamOptions b (->stream-options stream-options)))
    (when multi-agent (.multiAgent b (->multi-agent multi-agent)))
    (when (or json-schema verbosity) (.text b (->text-config json-schema verbosity)))
    (when conversation (.conversation b ^String conversation))
    (when moderation (.moderation b (->moderation moderation)))
    (.build b)))

(defn- ->retrieve-params ^ResponseRetrieveParams
  [^String response-id {:keys [include include-obfuscation starting-after betas]}]
  (let [^ResponseRetrieveParams$Builder b (ResponseRetrieveParams/builder)]
    (.responseId b response-id)
    (doseq [i include] (.addInclude b (BetaResponseIncludable/of (impl/enum-name i))))
    (when (some? include-obfuscation) (.includeObfuscation b (boolean include-obfuscation)))
    (when (some? starting-after) (.startingAfter b (long starting-after)))
    (doseq [beta betas]
      (.addBeta b (com.openai.models.beta.responses.ResponseRetrieveParams$Beta/of
                   (impl/enum-name beta))))
    (.build b)))

(defn- ->cancel-params ^ResponseCancelParams [^String response-id]
  (let [^ResponseCancelParams$Builder b (ResponseCancelParams/builder)]
    (.responseId b response-id)
    (.build b)))

(defn- ->delete-params ^ResponseDeleteParams [^String response-id]
  (let [^ResponseDeleteParams$Builder b (ResponseDeleteParams/builder)]
    (.responseId b response-id)
    (.build b)))

(defn- ->compact-params ^ResponseCompactParams
  [{:keys [model previous-response-id input instructions prompt-cache-key service-tier betas]}]
  (let [^ResponseCompactParams$Builder b (ResponseCompactParams/builder)]
    (when model (.model b ^String model))
    (when previous-response-id (.previousResponseId b ^String previous-response-id))
    (when input
      (if (string? input)
        (.input b ^String input)
        (.inputOfBetaResponseInputItems b ^java.util.List (mapv ->input-item input))))
    (when instructions (.instructions b ^String instructions))
    (when prompt-cache-key (.promptCacheKey b ^String prompt-cache-key))
    (when service-tier (.serviceTier b (ResponseCompactParams$ServiceTier/of (impl/enum-name service-tier))))
    (doseq [beta betas]
      (.addBeta b (com.openai.models.beta.responses.ResponseCompactParams$Beta/of
                   (impl/enum-name beta))))
    (.build b)))

(defn- ->input-item-list-params ^InputItemListParams
  [^String response-id {:keys [after include limit order betas]}]
  (let [^InputItemListParams$Builder b (InputItemListParams/builder)]
    (.responseId b response-id)
    (when after (.after b ^String after))
    (doseq [i include] (.addInclude b (BetaResponseIncludable/of (impl/enum-name i))))
    (when limit (.limit b (long limit)))
    (when order (.order b (InputItemListParams$Order/of (impl/enum-name order))))
    (doseq [beta betas]
      (.addBeta b (com.openai.models.beta.responses.inputitems.InputItemListParams$Beta/of
                   (impl/enum-name beta))))
    (.build b)))

(defn- ->input-token-count-params ^InputTokenCountParams
  [{:keys [model input instructions previous-response-id parallel-tool-calls truncation betas]}]
  (let [^InputTokenCountParams$Builder b (InputTokenCountParams/builder)]
    (when model (.model b ^String model))
    (when input
      (if (string? input)
        (.input b ^String input)
        (.inputOfBetaResponseInputItems b ^java.util.List (mapv ->input-item input))))
    (when instructions (.instructions b ^String instructions))
    (when previous-response-id (.previousResponseId b ^String previous-response-id))
    (when (some? parallel-tool-calls) (.parallelToolCalls b (boolean parallel-tool-calls)))
    (when truncation (.truncation b (InputTokenCountParams$Truncation/of (impl/enum-name truncation))))
    (doseq [beta betas]
      (.addBeta b (com.openai.models.beta.responses.inputtokens.InputTokenCountParams$Beta/of
                   (impl/enum-name beta))))
    (.build b)))

(defn- normalize-value [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[k v]]
                 [k (if (and (#{:type :status :role} k) (string? v))
                      (impl/->keyword v)
                      (normalize-value v))]))
          value)
    (vector? value) (mapv normalize-value value)
    :else value))

(defn- beta-response-data->map ^clojure.lang.IPersistentMap [m]
  (let [items (mapv normalize-value (:output m))]
    (cond-> {:id (:id m)
             :model (:model m)
             :output items
             :text (impl/output-text items)
             :created-at (:created-at m)}
      (:status m) (assoc :status (:status m))
      (:usage m) (assoc :usage (:usage m))
      (:error m) (assoc :error (:error m))
      (:incomplete-details m) (assoc :incomplete-details (:incomplete-details m))
      (:previous-response-id m) (assoc :previous-response-id (:previous-response-id m))
      (:prompt m) (assoc :prompt (:prompt m))
      (:prompt-cache-retention m) (assoc :prompt-cache-retention (:prompt-cache-retention m)))))

(defn- beta-response->map ^clojure.lang.IPersistentMap [^BetaResponse response]
  (beta-response-data->map (normalize-value (impl/sdk-object->clj response))))

(defn- beta-compacted-response->map ^clojure.lang.IPersistentMap
  [^BetaCompactedResponse response]
  (let [m (normalize-value (impl/sdk-object->clj response))
        items (mapv normalize-value (:output m))]
    {:id (:id m)
     :output items
     :text (impl/output-text items)
     :usage (:usage m)
     :created-at (:created-at m)}))

(defn- input-token-count-response->map ^clojure.lang.IPersistentMap
  [^InputTokenCountResponse response]
  {:input-tokens (:input-tokens (impl/sdk-object->clj response))})

(defn- event->map ^clojure.lang.IPersistentMap [^BetaResponseStreamEvent event]
  (let [m (normalize-value (impl/sdk-object->clj event))
        type (some-> (:type m) name
                     (str/replace-first #"^response[.-]" "")
                     (str/replace "_" "-")
                     (str/replace "." "-")
                     keyword)
        type (case type
               :text-delta :output-text-delta
               :text-done :output-text-done
               type)]
    (case type
      :output-text-delta (select-keys (assoc m :type type) [:type :delta :item-id :output-index])
      :output-text-done (select-keys (assoc m :type type) [:type :text :item-id :output-index])
      :created {:type :created}
      :in-progress {:type :in-progress}
      :completed {:type :completed
                  :response (beta-response-data->map (:response m))}
      :incomplete {:type :incomplete
                   :response (beta-response-data->map (:response m))}
      :failed {:type :failed
               :response (beta-response-data->map (:response m))}
      :queued {:type :queued
               :response (beta-response-data->map (:response m))}
      :error (select-keys (assoc m :type type) [:type :message :code])
      (assoc m :type type))))

(defn- drain-stream ^String [^StreamResponse stream on-event]
  (let [sb (StringBuilder.)]
    (doseq [event (iterator-seq (.iterator (.stream stream)))]
      (let [m (event->map event)]
        (when (= :output-text-delta (:type m)) (.append sb ^String (:delta m)))
        (when on-event (on-event m))))
    (str sb)))

(defn create-response [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (beta-response->map (.create svc (->params req))))))

(defn count-input-tokens [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))
          ^InputTokenService tokens (.inputTokens svc)]
      (input-token-count-response->map (.count tokens (->input-token-count-params req))))))

(defn stream ^String [^OpenAIClient client req on-event]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (with-open [^StreamResponse stream (.createStreaming svc (->params req))]
        (drain-stream stream on-event)))))

(defn retrieve-streaming ^String [^OpenAIClient client ^String response-id on-event]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (with-open [^StreamResponse stream
                  (.retrieveStreaming svc (->retrieve-params response-id {}))]
        (drain-stream stream on-event)))))

(defn stream-text [^OpenAIClient client req on-text]
  (stream client req
          (fn [m]
            (when (and on-text (= :output-text-delta (:type m)))
              (on-text (:delta m))))))

(defn get-response [^OpenAIClient client ^String response-id]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (beta-response->map (.retrieve svc (->retrieve-params response-id {}))))))

(defn list-input-items
  ([^OpenAIClient client ^String response-id] (list-input-items client response-id {}))
  ([^OpenAIClient client ^String response-id opts]
   (impl/with-api-errors
     (let [^ResponseService svc (.. client (beta) (responses))
           ^InputItemService items (.inputItems svc)
           ^InputItemListPage page (.list items (->input-item-list-params response-id opts))]
       (mapv #(normalize-value (impl/sdk-object->clj %)) (impl/all-pages page))))))

(defn delete-response [^OpenAIClient client ^String response-id]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (.delete svc (->delete-params response-id)))
    nil))

(defn cancel-response [^OpenAIClient client ^String response-id]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (beta-response->map (.cancel svc (->cancel-params response-id))))))

(defn compact [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (beta-compacted-response->map (.compact svc (->compact-params req))))))
