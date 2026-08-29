(ns openai.chatkit-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.impl]
            [openai.chatkit :as chatkit])
  (:import (com.openai.core JsonValue)
           (com.openai.models.beta.chatkit ChatKitWorkflow ChatKitWorkflow$StateVariables
                                           ChatKitWorkflow$Tracing)
           (com.openai.models.beta.chatkit.sessions SessionCreateParams)
           (com.openai.models.beta.chatkit.threads ChatKitThread ChatKitThread$Status
                                                   ChatKitThreadItemList$Data
                                                   ChatKitThreadUserMessageItem
                                                   ChatSession ChatSessionAutomaticThreadTitling
                                                   ChatSessionChatKitConfiguration ChatSessionFileUpload
                                                   ChatSessionHistory
                                                   ChatSessionRateLimits ChatSessionStatus
                                                   ThreadDeleteResponse)))

(set! *warn-on-reflection* true)

(deftest translates-session-create
  (let [^SessionCreateParams p (#'chatkit/->create-params
                                 {:workflow {:id "wf_x" :version "1"}
                                  :user "u"})]
    (is (= "wf_x" (.id (.workflow p))))
    (is (= "u" (.user p))))
  (testing "workflow is required"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Missing required key :workflow"
                          (#'chatkit/->create-params {}))))
  (testing "workflow id is required"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Missing required key :id"
                          (#'chatkit/->create-params {:workflow {}})))))

(deftest converts-session-response
  (let [session (-> (ChatSession/builder)
                    (.id "sess_1")
                    (.clientSecret "secret")
                    (.expiresAt 10)
                    (.maxRequestsPer1Minute 20)
                    (.object_ (JsonValue/from "chat.session"))
                    (.rateLimits (-> (ChatSessionRateLimits/builder)
                                     (.maxRequestsPer1Minute 20) (.build)))
                    (.status (ChatSessionStatus/of "active"))
                    (.user "u")
                    (.workflow (-> (ChatKitWorkflow/builder) (.id "wf_1")
                                   (.stateVariables (-> (ChatKitWorkflow$StateVariables/builder)
                                                        (.build)))
                                   (.tracing (-> (ChatKitWorkflow$Tracing/builder) (.enabled false) (.build)))
                                   (.version "1")
                                   (.build)))
                    (.chatkitConfiguration
                     (-> (ChatSessionChatKitConfiguration/builder)
                         (.automaticThreadTitling
                          (-> (ChatSessionAutomaticThreadTitling/builder) (.enabled false) (.build)))
                         (.fileUpload (-> (ChatSessionFileUpload/builder) (.enabled false)
                                          (.maxFileSize 1) (.maxFiles 1) (.build)))
                         (.history (-> (ChatSessionHistory/builder) (.enabled false)
                                       (.recentThreads 1) (.build)))
                         (.build)))
                    (.build))]
    (is (= {:id "sess_1" :client-secret "secret" :expires-at 10
            :max-requests-per-1-minute 20 :status :active :user "u"
            :workflow {:id "wf_1" :state-variables {} :tracing {:enabled false} :version "1"}
            :rate-limits {:max-requests-per-1-minute 20}
            :chatkit-configuration {:automatic-thread-titling {:enabled false}
                                    :file-upload {:enabled false :max-file-size 1 :max-files 1}
                                    :history {:enabled false :recent-threads 1}}}
           (#'chatkit/chat-session->map session)))))

(deftest converts-thread-and-delete-responses
  (let [thread (-> (ChatKitThread/builder)
                   (.id "thread_1") (.createdAt 10)
                   (.object_ (JsonValue/from "thread"))
                   (.status (ChatKitThread$Status/ofActive))
                   (.user "u") (.title "Title") (.build))
        deletion (-> (ThreadDeleteResponse/builder)
                     (.id "thread_1") (.deleted true)
                     (.object_ (JsonValue/from "thread.deleted")) (.build))]
    (is (= {:id "thread_1" :created-at 10 :status :active :user "u" :title "Title"}
           (#'chatkit/chatkit-thread->map thread)))
    (is (= {:id "thread_1" :deleted true}
           (#'chatkit/delete-response->map deletion)))))

(deftest converts-thread-item-union-with-sdk-json-round-trip
  (let [item (let [^java.util.List attachments (java.util.ArrayList.)
                   b (ChatKitThreadUserMessageItem/builder)]
               (.id b "item_1")
               (.attachments b attachments)
               (.addInputTextContent b "Hello")
               (.createdAt b 10)
               (.inferenceOptions b (java.util.Optional/empty))
               (.object_ b (JsonValue/from "thread.item"))
               (.threadId b "thread_1")
               (.type b (JsonValue/from "user_message"))
               (.build b))
        union (ChatKitThreadItemList$Data/ofChatKitUserMessage item)]
    (is (= {:id "item_1" :attachments [] :content [{:text "Hello" :type "input_text"}]
            :created-at 10 :inference-options nil :object "thread.item" :thread-id "thread_1"
            :type "user_message"}
           (openai.impl/sdk-object->clj union)))))
