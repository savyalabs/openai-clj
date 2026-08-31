(ns openai.beta.responses-test
  (:require [clojure.test :refer [deftest is]]
            [openai.impl :as impl]
            [openai.beta.responses :as responses])
  (:import (com.openai.core JsonValue)
           (com.openai.models.beta.responses BetaCompactedResponse
                                             BetaResponse
                                             BetaResponseStatus
                                             BetaResponseStreamEvent
                                             BetaResponseTextDeltaEvent
                                             BetaResponseUsage
                                             BetaResponseUsage$InputTokensDetails
                                             BetaResponseUsage$OutputTokensDetails
                                             BetaResponseFunctionToolCall
                                             BetaResponseOutputItem
                                             BetaResponseOutputItem$AgentMessage
                                             BetaResponseOutputMessage
                                             BetaResponseOutputText
                                             BetaToolChoiceOptions
                                             ResponseCreateParams
                                             ResponseCreateParams$Beta)
           (com.openai.models.beta.responses.inputtokens InputTokenCountResponse)))

(defn- empty-usage []
  (-> (BetaResponseUsage/builder)
      (.inputTokens 10)
      (.inputTokensDetails
       (-> (BetaResponseUsage$InputTokensDetails/builder)
           (.cacheWriteTokens 0)
           (.cachedTokens 0)
           (.build)))
      (.outputTokens 20)
      (.outputTokensDetails
       (-> (BetaResponseUsage$OutputTokensDetails/builder)
           (.reasoningTokens 0)
           (.build)))
      (.totalTokens 30)
      (.build)))

(defn- ex-data-for [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (ex-data e))))

(defn- beta-response
  ([] (beta-response []))
  ([items]
  (let [^java.util.Optional empty (java.util.Optional/empty)]
    (-> (BetaResponse/builder)
        (.id "resp_beta")
        (.createdAt 1234.5)
        (.error empty)
        (.incompleteDetails empty)
        (.instructions empty)
        (.metadata empty)
        (.model "gpt-beta")
        (.object_ (JsonValue/from "response"))
        (.output items)
        (.parallelToolCalls false)
        (.temperature empty)
        (.toolChoice BetaToolChoiceOptions/AUTO)
        (.tools [])
        (.topP empty)
        (.background empty)
        (.completedAt empty)
        (.conversation empty)
        (.maxOutputTokens empty)
        (.maxToolCalls empty)
        (.previousResponseId empty)
        (.prompt empty)
        (.promptCacheKey empty)
        (.promptCacheRetention empty)
        (.reasoning empty)
        (.safetyIdentifier empty)
        (.serviceTier empty)
        (.status BetaResponseStatus/COMPLETED)
        (.truncation empty)
        (.usage (empty-usage))
        (.build)))))

(deftest translates-beta-create-params
  (let [p (#'responses/->params
           {:model "gpt-beta"
            :input "hello"
            :betas [:responses-multi-agent-v1]
            :multi-agent {:enabled true :max-concurrent-subagents 3}})]
    (is (instance? com.openai.models.beta.responses.ResponseCreateParams p))
    (is (= "gpt-beta" (.asString (impl/opt-get (.model p)))))
    (is (= "hello" (.asText (impl/opt-get (.input p)))))
    (is (= ["responses_multi_agent_v1"]
           (mapv #(.asString ^ResponseCreateParams$Beta %) (impl/opt-get (.betas p)))))
    (let [multi-agent (impl/opt-get (.multiAgent p))]
      (is (true? (.enabled multi-agent)))
      (is (= 3 (impl/opt-get (.maxConcurrentSubagents multi-agent)))))
    (let [^ResponseCreateParams p (#'responses/->params
                                   {:model "gpt-beta"
                                    :input [{:role :user :content "hello"}]})
          input (impl/opt-get (.input p))]
      (is (.isBetaResponse input))
      (is (= 1 (count (.asBetaResponse input)))))))

(deftest translates-beta-create-request-surface
  (let [p (#'responses/->params
           {:model "gpt-beta"
            :input [{:role :user
                     :content [{:type :text :text "look"}
                               {:type :image :image-url "https://example.test/cat.png"
                                :detail :high}
                               {:type :file :filename "notes.txt"
                                :file-data "data:text/plain;base64,AAAA"}]}]
            :tools [{:type :function
                     :name "get_weather"
                     :parameters {:type "object"}}
                    {:type :programmatic-tool-calling}]
            :json-schema {:name "answer"
                          :schema {:type "object"}}
            :verbosity :low
            :conversation "conv_beta"
            :moderation {:model "omni-moderation-latest"}
            :prompt {:id "pmpt_beta"
                     :version "3"
                     :variables {:city "Denver"}}
            :context-management [{:type :compaction
                                  :compact-threshold 2000}]
            :prompt-cache-retention "24h"})
        prompt (impl/opt-get (.prompt p))
        variables (impl/opt-get (.variables prompt))
        context (first (impl/opt-get (.contextManagement p)))
        input (impl/opt-get (.input p))
        message (.asMessage (first (.asBetaResponse input)))
        content (.content message)
        tools (impl/opt-get (.tools p))]
    (is (= "get_weather" (.name (.asFunction (first tools)))))
    (is (.isProgrammaticToolCalling (second tools)))
    (is (= "low" (.asString (impl/opt-get (.verbosity (impl/opt-get (.text p)))))))
    (is (= "conv_beta" (.asId (impl/opt-get (.conversation p)))))
    (is (= "omni-moderation-latest" (.model (impl/opt-get (.moderation p)))))
    (is (= "pmpt_beta" (.id prompt)))
    (is (= "3" (impl/opt-get (.version prompt))))
    (is (= "Denver"
           (.asStringOrThrow (get (._additionalProperties variables) "city"))))
    (is (= "compaction" (.type context)))
    (is (= 2000 (impl/opt-get (.compactThreshold context))))
    (is (= "24h" (.asString (impl/opt-get (.promptCacheRetention p)))))
    (is (= "look" (.text (.asInputText (first content)))))
    (is (= "https://example.test/cat.png"
           (impl/opt-get (.imageUrl (.asInputImage (second content))))))
    (is (= "notes.txt"
           (impl/opt-get (.filename (.asInputFile (nth content 2))))))))

(deftest translates-beta-agent-tool-output-inputs
  (let [p (#'responses/->params
           {:model "gpt-beta"
            :input [{:type :computer-call-output
                     :call-id "call_computer"
                     :output {:image-url "data:image/png;base64,abc"}
                     :acknowledged-safety-checks [{:id "safe_1"}]}
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
        xs (.asBetaResponse (impl/opt-get (.input p)))
        computer (.asComputerCallOutput (nth xs 0))
        local-shell (.asLocalShellCallOutput (nth xs 1))
        shell (.asShellCallOutput (nth xs 2))
        patch (.asApplyPatchCallOutput (nth xs 3))
        custom (.asCustomToolCallOutput (nth xs 4))
        search (.asToolSearchOutput (nth xs 5))
        approval (.asMcpApprovalResponse (nth xs 6))]
    (is (= "data:image/png;base64,abc" (impl/opt-get (.imageUrl (.output computer)))))
    (is (= "safe_1" (-> computer .acknowledgedSafetyChecks impl/opt-get first .id)))
    (is (= "ok" (.output local-shell)))
    (is (= 0 (-> shell .output first .outcome .asExit .exitCode)))
    (is (= "done" (impl/opt-get (.output patch))))
    (is (= "{\"ok\":true}" (.asString (.output custom))))
    (is (.isCustom (first (.tools search))))
    (is (true? (.approve approval)))
    (is (= "trusted" (impl/opt-get (.reason approval))))))

(deftest accepts-all-beta-response-input-item-variants
  (doseq [[type item? item]
          [[:additional-tools #(.isAdditionalTools %) {:type :additional-tools :tools []}]
           [:agent-message #(.isAgentMessage %) {:type :agent-message :author "planner" :recipient "user" :content "note"}]
           [:apply-patch-call #(.isApplyPatchCall %) {:type :apply-patch-call :call-id "c" :operation {:type :update-file :path "p" :diff "d"} :status :completed}]
           [:apply-patch-call-output #(.isApplyPatchCallOutput %) {:type :apply-patch-call-output :call-id "c" :status :completed :output "ok"}]
           [:beta-easy-input-message #(.isBetaEasyInputMessage %) {:type :beta-easy-input-message :role :user :content "x"}]
           [:beta-response-output-message #(.isBetaResponseOutputMessage %) {:type :beta-response-output-message :id "m" :role :assistant :status :completed :content "x"}]
           [:code-interpreter-call #(.isCodeInterpreterCall %) {:type :code-interpreter-call :id "c" :code "x" :container-id "ct" :status :completed :outputs []}]
           [:compaction #(.isCompaction %) {:type :compaction :id "c"}]
           [:compaction-trigger #(.isCompactionTrigger %) {:type :compaction-trigger}]
           [:computer-call #(.isComputerCall %) {:type :computer-call :id "c" :call-id "cc" :pending-safety-checks [] :status :completed}]
           [:computer-call-output #(.isComputerCallOutput %) {:type :computer-call-output :call-id "cc" :output {:image-url "data:image/png;base64,x"}}]
           [:custom-tool-call #(.isCustomToolCall %) {:type :custom-tool-call :id "c" :call-id "cc" :name "n" :input "x"}]
           [:custom-tool-call-output #(.isCustomToolCallOutput %) {:type :custom-tool-call-output :call-id "cc" :output "x"}]
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
           [:multi-agent-call #(.isMultiAgentCall %) {:type :multi-agent-call :action :spawn-agent :call-id "c" :arguments {:x 1}}]
           [:multi-agent-call-output #(.isMultiAgentCallOutput %) {:type :multi-agent-call-output :action :spawn-agent :call-id "c" :output "ok"}]
           [:program #(.isProgram %) {:type :program :id "p" :call-id "c" :code "x" :fingerprint "f"}]
           [:program-output #(.isProgramOutput %) {:type :program-output :id "p" :call-id "c" :result "x" :status :completed}]
           [:reasoning #(.isReasoning %) {:type :reasoning :id "r" :summary []}]
           [:shell-call #(.isShellCall %) {:type :shell-call :id "s" :call-id "c" :action :exec :environment :local :status :completed}]
           [:shell-call-output #(.isShellCallOutput %) {:type :shell-call-output :id "s" :call-id "c" :output [{:stdout "x" :stderr "" :exit-code 0}] :status :completed}]
           [:tool-search-call #(.isToolSearchCall %) {:type :tool-search-call :id "t" :call-id "c" :arguments {} :execution :client :status :completed}]
           [:tool-search-output #(.isToolSearchOutput %) {:type :tool-search-output :id "t" :call-id "c" :tools [] :execution :client :status :completed}]
           [:web-search-call #(.isWebSearchCall %) {:type :web-search-call :id "w" :action {:type :search :query "q" :queries ["q"] :sources []} :status :completed}]]]
    (is (item? (#'responses/->input-item item)) (str type))))

(deftest translates-beta-additional-input-variants
  (let [p (#'responses/->params
           {:model "gpt-beta"
            :input [{:type :agent-message :author "planner" :recipient "user" :content "agent note"}
                    {:type :beta-easy-input-message :role :user :content "easy note"}
                    {:type :beta-response-output-message :id "msg_1"
                     :role :assistant :status :completed :content "output note"}
                    {:type :multi-agent-call
                     :action :spawn-agent
                     :call-id "call_1"
                     :arguments {:task "research"}}
                    {:type :multi-agent-call-output
                     :action :spawn-agent
                     :call-id "call_1"
                     :output "done"}]})
        xs (.asBetaResponse (impl/opt-get (.input p)))]
    (is (.isAgentMessage (nth xs 0)))
    (is (.isBetaEasyInputMessage (nth xs 1)))
    (is (.isBetaResponseOutputMessage (nth xs 2)))
    (is (.isMultiAgentCall (nth xs 3)))
    (is (.isMultiAgentCallOutput (nth xs 4)))))

(deftest translates-beta-tools
  (let [p (#'responses/->params
           {:model "gpt-beta"
            :input "hi"
            :tools [{:type :function :name "weather"}
                    {:type :web-search}
                    {:type :file-search :vector-store-ids ["vs_1"]}
                    {:type :mcp :server-label "docs"}
                    {:type :code-interpreter}
                    {:type :programmatic-tool-calling}
                    {:type :image-generation :quality :high}
                    {:type :computer}
                    {:type :local-shell}
                    {:type :shell :environment :local}
                    {:type :apply-patch}
                    {:type :custom :name "lint" :format :text}
                    {:type :tool-search :parameters {:query "lint"}}]})
        tools (impl/opt-get (.tools p))]
    (is (.isFunction (nth tools 0)))
    (is (.isWebSearch (nth tools 1)))
    (is (= ["vs_1"] (vec (.vectorStoreIds (.asFileSearch (nth tools 2))))))
    (is (.isMcp (nth tools 3)))
    (is (.isCodeInterpreter (nth tools 4)))
    (is (.isProgrammaticToolCalling (nth tools 5)))
    (is (.isImageGeneration (nth tools 6)))
    (is (.isComputer (nth tools 7)))
    (is (.isLocalShell (nth tools 8)))
    (is (.isShell (nth tools 9)))
    (is (.isApplyPatch (nth tools 10)))
    (is (.isCustom (nth tools 11)))
    (is (.isToolSearch (nth tools 12)))))

(deftest rejects-explicitly-empty-beta-web-search-domain-allow-list
  (is (= {:openai/error :empty-allow-list :option :allowed-domains}
         (ex-data-for #( #'responses/->params
                        {:model "gpt-beta"
                         :input "hi"
                         :tools [{:type :web-search
                                  :allowed-domains []}]})))))

(deftest rejects-explicitly-empty-beta-mcp-allow-list
  (is (= {:openai/error :empty-allow-list :option :allowed-tools}
         (ex-data-for #( #'responses/->params
                        {:model "gpt-beta"
                         :input "hi"
                         :tools [{:type :mcp
                                  :server-label "docs"
                                  :allowed-tools []}]})))))

(deftest translates-beta-tool-choice
  (let [function-choice (impl/opt-get
                         (.toolChoice (#'responses/->params
                                       {:model "gpt-beta"
                                        :input "hi"
                                        :tool-choice {:type :function :name "weather"}})))
        programmatic-choice (impl/opt-get
                             (.toolChoice (#'responses/->params
                                           {:model "gpt-beta"
                                            :input "hi"
                                            :tool-choice {:type :programmatic-tool-calling}})))]
    (is (= "weather" (.name (.asBetaToolChoiceFunction function-choice))))
    (is (.isBetaSpecificProgrammaticToolCallingParam programmatic-choice))))

(deftest translates-beta-operation-params
  (let [retrieve (#'responses/->retrieve-params "resp_1" {:include [:reasoning-encrypted-content]
                                                            :include-obfuscation true
                                                            :starting-after 2})
        cancel (#'responses/->cancel-params "resp_1")
        delete (#'responses/->delete-params "resp_1")
        compact (#'responses/->compact-params {:model "gpt-beta"
                                               :previous-response-id "resp_1"})
        items (#'responses/->input-item-list-params "resp_1" {:after "item_1"
                                                               :limit 5
                                                               :order :desc})]
    (is (= "resp_1" (impl/opt-get (.responseId retrieve))))
    (is (true? (impl/opt-get (.includeObfuscation retrieve))))
    (is (= 2 (impl/opt-get (.startingAfter retrieve))))
    (is (= "resp_1" (impl/opt-get (.responseId cancel))))
    (is (= "resp_1" (impl/opt-get (.responseId delete))))
    (is (= "resp_1" (impl/opt-get (.previousResponseId compact))))
    (is (= "item_1" (impl/opt-get (.after items))))
    (is (= 5 (impl/opt-get (.limit items))))
    (is (= "desc" (.asString (impl/opt-get (.order items)))))))

(deftest maps-beta-responses
  (is (= {:id "resp_beta"
          :model "gpt-beta"
          :output []
          :text ""
          :created-at 1234.5
          :status :completed
          :usage {:input-tokens 10
                  :input-tokens-details {:cache-write-tokens 0 :cached-tokens 0}
                  :output-tokens 20
                  :output-tokens-details {:reasoning-tokens 0}
                  :total-tokens 30}}
         (#'responses/beta-response->map (beta-response)))))

(deftest maps-beta-response-prompt-fields
  (let [prompt (-> (com.openai.models.beta.responses.BetaResponsePrompt/builder)
                   (.id "pmpt_beta")
                   (.version "4")
                   (.build))
        response (-> (beta-response)
                     .toBuilder
                     (.prompt (java.util.Optional/of prompt))
                     (.promptCacheRetention
                      (java.util.Optional/of
                       (com.openai.models.beta.responses.BetaResponse$PromptCacheRetention/of
                        "24h")))
                     (.build))
        m (#'responses/beta-response->map response)]
    (is (= "pmpt_beta" (get-in m [:prompt :id])))
    (is (= "4" (get-in m [:prompt :version])))
    (is (= "24h" (:prompt-cache-retention m)))))

(deftest round-trips-beta-output-function-call-as-input
  (let [item (BetaResponseOutputItem/ofFunctionCall
              (-> (BetaResponseFunctionToolCall/builder)
                  (.id "fc_beta")
                  (.callId "call_beta")
                  (.name "weather")
                  (.arguments "{\"city\":\"Denver\"}")
                  (.build)))
        m (-> (beta-response [item])
              (#'responses/beta-response->map)
              :output
              first)
        rebuilt (#'responses/->input-item m)]
    (is (.isFunctionCall rebuilt))
    (is (= "call_beta" (.callId (.asFunctionCall rebuilt))))
    (is (= "{\"city\":\"Denver\"}"
           (.arguments (.asFunctionCall rebuilt))))))

(deftest round-trips-beta-output-message-as-input
  (let [item (BetaResponseOutputItem/ofMessage
              (-> (BetaResponseOutputMessage/builder)
                  (.id "msg_beta")
                  (.role (JsonValue/from "assistant"))
                  (.status (com.openai.models.beta.responses.BetaResponseOutputMessage$Status/of "completed"))
                  (.addContent
                   (-> (BetaResponseOutputText/builder)
                       (.text "hello")
                       (.annotations [])
                       (.build)))
                  (.build)))
        m (-> (beta-response [item])
              (#'responses/beta-response->map)
              :output
              first)
        rebuilt (#'responses/->input-item m)]
    (is (.isBetaResponseOutputMessage rebuilt))
    (is (= "msg_beta" (.id (.asBetaResponseOutputMessage rebuilt))))))

(deftest round-trips-beta-agent-output-as-input
  (let [item (BetaResponseOutputItem/ofAgentMessage
              (-> (BetaResponseOutputItem$AgentMessage/builder)
                  (.id "agent_msg")
                  (.author "planner")
                  (.recipient "user")
                  (.addTextContent "hello")
                  (.build)))
        m (-> (beta-response [item])
              (#'responses/beta-response->map)
              :output
              first)
        rebuilt (#'responses/->input-item m)]
    (is (.isAgentMessage rebuilt))
    (is (= "agent_msg" (impl/opt-get (.id (.asAgentMessage rebuilt)))))))

(deftest maps-beta-compacted-response
  (let [r (-> (BetaCompactedResponse/builder)
              (.id "resp_compact")
              (.createdAt 1234)
              (.object_ (JsonValue/from "response.compaction"))
              (.output [])
              (.usage (empty-usage))
              (.build))]
    (is (= {:id "resp_compact" :output [] :text ""
            :usage {:input-tokens 10
                    :input-tokens-details {:cache-write-tokens 0 :cached-tokens 0}
                    :output-tokens 20
                    :output-tokens-details {:reasoning-tokens 0}
                    :total-tokens 30}
            :created-at 1234}
           (#'responses/beta-compacted-response->map r)))))

(deftest maps-beta-token-count-response
  (let [r (-> (InputTokenCountResponse/builder)
              (.inputTokens 42)
              (.object_ (JsonValue/from "response.input_tokens"))
              (.build))]
    (is (= {:input-tokens 42} (#'responses/input-token-count-response->map r)))))

(deftest maps-beta-stream-events
  (let [event (BetaResponseStreamEvent/ofResponseOutputTextDelta
               (-> (BetaResponseTextDeltaEvent/builder)
                   (.contentIndex 0)
                   (.delta "Hel")
                   (.itemId "msg_1")
                   (.logprobs [])
                   (.outputIndex 0)
                   (.sequenceNumber 1)
                   (.build)))]
    (is (= {:type :output-text-delta
            :delta "Hel"
            :item-id "msg_1"
            :output-index 0}
           (#'responses/event->map event)))))
