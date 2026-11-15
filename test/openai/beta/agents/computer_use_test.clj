(ns openai.beta.agents.computer-use-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.impl :as impl])
  (:import (com.openai.models.beta.agents AgentOutputItem
                                           AgentSession$RequiredAction
                                           AgentSessionInputParam
                                           AgentSessionItem
                                           AgentTool
                                           AgentToolParam
                                           EnvironmentParam
                                           PersistedAgentToolParam)
           (com.openai.models.beta.agents.environments.templates TemplateCreateParams$Body
                                                                    TemplateUpdateParams$Body)))

(set! *warn-on-reflection* true)

(deftest generic-sdk-input-conversion-covers-computer-use-tools-and-environments
  (testing "tool unions accept the new computer-use member"
    (let [^AgentToolParam tool (impl/sdk-input-object {:type :computer-use}
                                                       AgentToolParam)
          ^PersistedAgentToolParam persisted
          (impl/sdk-input-object {:type :computer-use} PersistedAgentToolParam)]
      (is (.isComputerUse tool))
      (is (.isComputerUse persisted))))
  (testing "hosted environments accept desktop and blocked domains"
    (let [^EnvironmentParam environment
          (impl/sdk-input-object
           {:type :openai-hosted
            :container-size :medium
            :desktop {:enabled true}
            :network {:access :restricted
                      :blocked-domains ["blocked.example"]}}
           EnvironmentParam)]
      (is (.isOpenAIHosted environment))
      (is (= {:type "openai_hosted"
              :container-size "medium"
              :desktop {:enabled true}
              :network {:access "restricted"
                        :blocked-domains ["blocked.example"]}}
             (impl/sdk-object->clj environment))))))

(deftest generic-sdk-conversion-covers-function-tool-async-and-mcp-approval
  (let [^AgentTool tool (impl/sdk-input-object
                          {:type :function :name "search" :parameters {} :async true}
                          AgentTool)
        ^AgentSessionInputParam input (impl/sdk-input-object
                                       {:type :agent.session.input.mcp-approval-request-result
                                        :request-id "request_123"
                                        :response {:decision :approve}}
                                       AgentSessionInputParam)]
    (is (true? (.get (.async (.asFunction tool)))))
    (is (.isAgentSessionInputMcpApprovalRequestResult input))
    (is (= :approve (-> input impl/sdk-object->clj :response :decision impl/->keyword)))))

(deftest generic-sdk-conversion-covers-mcp-approval-required-action
  (let [^AgentSession$RequiredAction action
        (impl/sdk-input-object
         {:type :mcp-approval-request :request-id "request_123" :turn-id "turn_123"
          :request {:message "Approve?" :server-label "tools" :arguments {} :name "search"}}
         AgentSession$RequiredAction)]
    (is (.isMcpApprovalRequest action))
    (is (= "request_123" (:request-id (impl/sdk-object->clj action))))))

(deftest generic-sdk-input-conversion-covers-template-desktop-settings
  (doseq [target [TemplateCreateParams$Body TemplateUpdateParams$Body]]
    (let [body (impl/sdk-input-object {:desktop {:enabled true}} target)]
      (is (= {:desktop {:enabled true}}
             (impl/sdk-object->clj body))))))

(deftest generic-sdk-input-conversion-covers-computer-use-approval-responses
  (doseq [response [{:type :browser-authentication-submit
                     :action :submit
                     :fields [{:id "username" :value "savya"}]
                     :selected-option "password"}
                    {:type :browser-authentication-cancel :action :cancel}
                    {:type :browser-origin-access :decision :allow}]]
    (let [^AgentSessionInputParam input (impl/sdk-input-object
                 {:type :agent.session.input.computer-use-approval-request-result
                  :request-id "request_123"
                  :response response}
                 AgentSessionInputParam)]
      (is (.isAgentSessionInputComputerUseApprovalRequestResult input))
      (is (= "request_123"
             (:request-id (impl/sdk-object->clj input)))))))

(deftest generic-sdk-conversion-covers-computer-use-items-and-required-actions
  (testing "session and output item unions accept computer-use calls"
    (let [item {:type :computer-use-call
                :id "item_123"
                :status :completed
                :turn-id "turn_123"}
          ^AgentSessionItem session-item (impl/sdk-input-object item AgentSessionItem)
          ^AgentOutputItem output-item (impl/sdk-input-object item AgentOutputItem)]
      (is (.isComputerUseCall session-item))
      (is (.isComputerUseCall output-item))
      (is (= "computer_use_call"
             (:type (impl/sdk-object->clj session-item))))))
  (testing "required actions accept browser authentication and origin access"
    (doseq [request [{:type :browser-authentication
                      :credential-origin "https://accounts.example"
                      :fields [{:id "username" :label "Username"}]
                      :options [{:id "password" :label "Password"}]}
                     {:type :browser-origin-access
                      :origin "https://app.example"}]]
      (let [^AgentSession$RequiredAction action (impl/sdk-input-object
                    {:type :computer-use-approval-request
                     :request request
                     :request-id "request_123"
                     :turn-id "turn_123"}
                    AgentSession$RequiredAction)]
        (is (.isComputerUseApprovalRequest action))))))
