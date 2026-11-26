(ns openai.conversations-test
  (:require [clojure.test :refer [deftest is]] [openai.conversations :as conversations])
  (:import (com.openai.models.conversations ConversationCreateParams)
           (com.openai.models.responses EasyInputMessage EasyInputMessage$Content
                                         ResponseInputItem)))
(set! *warn-on-reflection* true)
(defn- opt [o] (when (.isPresent ^java.util.Optional o) (.get ^java.util.Optional o)))
(deftest translates-create-items
  (let [^ConversationCreateParams p
        (#'conversations/->create-params
         {:items [{:role :user :content "hello"}] :metadata {:topic "demo"}})
        ^ResponseInputItem item (first (opt (.items p)))
        ^EasyInputMessage message (.asEasyInputMessage item)
        ^EasyInputMessage$Content content (.content message)]
    (is (= 1 (count (opt (.items p)))))
    (is (= "hello" (.asTextInput content)))))

(deftest conversation-create-safety-identifier-reaches-sdk-params
  (let [^ConversationCreateParams p
        (#'conversations/->create-params {:safety-identifier "user_123"})]
    (is (= "user_123" (opt (.safetyIdentifier p))))))

(deftest exposes-item-prefix-api
  (doseq [sym '[item-create item-retrieve item-list item-delete]]
    (is (fn? (some-> (ns-resolve 'openai.conversations sym) deref)))))
