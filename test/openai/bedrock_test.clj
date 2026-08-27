(ns openai.bedrock-test
  (:require [clojure.test :refer [deftest is]])
  (:import (com.openai.client OpenAIClient)))

(defn- bedrock-client-var []
  (try
    (requiring-resolve 'openai.bedrock/client)
    (catch Throwable _ nil)))

(deftest bedrock-client-constructor-is-available
  (if (try
        (Class/forName "com.openai.client.okhttp.BedrockOpenAIOkHttpClient")
        true
        (catch ClassNotFoundException _ false))
    (is (fn? (some-> (bedrock-client-var) deref)))
    (is (nil? (bedrock-client-var))
        "The optional Bedrock namespace stays unavailable without :bedrock")))

(deftest bedrock-client-accepts-provider-options
  (if-let [client-fn (bedrock-client-var)]
    (let [client (@client-fn {:endpoint :runtime
                              :aws-region "us-west-2"
                              :aws-access-key-id "access-key"
                              :aws-secret-access-key "secret-key"
                              :aws-session-token "session-token"
                              :timeout-ms 1000
                              :max-retries 1})]
      (try
        (is (instance? OpenAIClient client))
        (finally
          (.close ^OpenAIClient client))))
    (is true "Provider options are exercised when the :bedrock alias is active")))
