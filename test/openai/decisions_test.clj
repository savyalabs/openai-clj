(ns openai.decisions-test
  (:require [clojure.test :refer [deftest is]]
            [openai.decisions]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.decisions DecisionCreateParams Decision)
           (com.openai.services.blocking DecisionService)))

(deftest builds-decision-params
  (let [^DecisionCreateParams params
        (#'openai.decisions/->create-params
         {:model "gpt-5" :input "Classify this" :safety-identifier "user_1"
          :questions [{:type :predicate :instructions "Is it safe?" :name "safe"}
                      {:type :choice :instructions "Choose" :name "choice"
                       :choices [{:value "yes" :description "positive"}
                                 {:value true :description "boolean"}]}
                      {:type :score :instructions "Score" :name "score"
                       :levels [{:label "low" :description "low score"}]}]})
        questions (.questions params)
        predicate (.asPredicate (first questions))
        choice (.asChoice (second questions))
        score (.asScore (nth questions 2))]
    (is (= "gpt-5" (.model params)))
    (is (= "Classify this" (.asString (.input params))))
    (is (= "user_1" (impl/opt-get (.safetyIdentifier params))))
    (is (.isPredicate (first questions)))
    (is (= "Is it safe?" (.asString (.instructions predicate))))
    (is (= "safe" (impl/opt-get (.name predicate))))
    (is (.isChoice (second questions)))
    (is (= "Choose" (.asString (.instructions choice))))
    (is (= "choice" (impl/opt-get (.name choice))))
    (is (= "yes" (.asString (.value (first (.choices choice))))))
    (is (= true (.asBool (.value (second (.choices choice))))))
    (is (.isScore (nth questions 2)))
    (is (= "Score" (.asString (.instructions score))))
    (is (= "score" (impl/opt-get (.name score))))
    (is (= "low" (.label (first (.levels score)))))
    (is (= "low score" (impl/opt-get (.description (first (.levels score))))))))

(deftest decisions-create-builds-params-on-4-91
  (let [^DecisionCreateParams params
        (#'openai.decisions/->create-params
         {:model "gpt-5" :input [{:role :user
                                   :content [{:type :input-text :text "look"}
                                             {:type :input-image :image-url "https://example.com/a.png"
                                              :detail :high}]}]
          :questions [{:type :predicate :instructions "safe?"}]})
        item (first (.asDecisionInputItems (.input params)))
        message (.asUser (.asDecisionInputMessage item))
        parts (.asParts (.content message))]
    (is (.isDecisionInputMessage item))
    (is (= "user" (.convert (._role message) String)))
    (is (.isInputText (first parts)))
    (is (= "look" (.text (.asInputText (first parts)))))
    (is (.isInputImage (second parts)))
    (is (= "https://example.com/a.png" (.imageUrl (.asInputImage (second parts)))))
    (is (= "high" (.asString (impl/opt-get (.detail (.asInputImage (second parts)))))))))

(deftest decisions-create-builds-tool-history-items-on-4-91
  (let [^DecisionCreateParams params
        (#'openai.decisions/->create-params
         {:model "gpt-5"
          :input [{:role :developer :content "Follow the policy."}
                  {:role :system :content [{:type :input-text :text "System context"}]}
                  {:role :assistant :content "I will call a tool."}
                  {:type :function-call :id "fc_1" :call-id "call_1"
                   :name "weather" :arguments "{\"city\":\"Paris\"}" :status :completed}
                  {:type :function-call-output :id "fco_1" :call-id "call_1"
                   :output "sunny" :status :completed}]
          :questions [{:type :predicate :instructions "Is it useful?"}]})
        items (.asDecisionInputItems (.input params))]
    (is (.isDeveloper (.asDecisionInputMessage (first items))))
    (is (.isSystem (.asDecisionInputMessage (second items))))
    (is (.isAssistant (.asDecisionInputMessage (nth items 2))))
    (is (.isFunctionCall (nth items 3)))
    (is (= "weather" (.name (.asFunctionCall (nth items 3)))))
    (is (.isFunctionCallOutput (nth items 4)))
    (is (= "sunny" (.asString (.output (.asFunctionCallOutput (nth items 4))))))))

(deftest converts-decision-result
  (let [^Decision decision
        (impl/sdk-input-object
         {:model "gpt-5"
          :answers [{:type "predicate" :name "safe" :probability 0.9}
                    {:type "choice" :name "choice" :choice "yes" :confidence 0.8
                     :probabilities [{:value "yes" :probability 0.8}]}
                    {:type "score" :name "score" :score 3.0 :confidence 0.7
                     :probabilities [{:label "high" :value 3 :probability 0.7}]}
                    {:type "refusal" :name "refusal"}]
          :usage {:input-tokens 1 :input-tokens-details {:cached-tokens 2 :cache-write-tokens 3}
                  :output-tokens 4 :output-tokens-details {:reasoning-tokens 5} :total-tokens 5}}
         Decision)]
    (is (= {:model "gpt-5"
            :answers [{:type :predicate :name "safe" :probability 0.9}
                      {:type :choice :name "choice" :choice "yes" :confidence 0.8
                       :probabilities [{:value "yes" :probability 0.8}]}
                      {:type :score :name "score" :score 3.0 :confidence 0.7
                       :probabilities [{:label "high" :value 3 :probability 0.7}]}
                      {:type :refusal :name "refusal"}]
            :usage {:input-tokens 1 :input-tokens-details {:cached-tokens 2 :cache-write-tokens 3}
                    :output-tokens 4 :output-tokens-details {:reasoning-tokens 5} :total-tokens 5}}
           (#'openai.decisions/decision->map decision)))))

(deftest rejects-unknown-question-type
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unsupported decision question"
                        (#'openai.decisions/->create-params
                         {:model "gpt-5" :input "x"
                          :questions [{:type :unknown :instructions "x"}]}))))

(deftest creates-decision-through-the-service
  (let [captured (atom nil)
        ^Decision decision
        (impl/sdk-input-object
         {:model "gpt-5"
          :answers [{:type "predicate" :name "safe" :probability 0.9}]
          :usage {:input-tokens 1 :input-tokens-details {:cached-tokens 0 :cache-write-tokens 0}
                  :output-tokens 2 :output-tokens-details {:reasoning-tokens 0} :total-tokens 3}}
         Decision)
        service (proxy [DecisionService] []
                  (create [params]
                    (reset! captured params)
                    decision))
        client (proxy [OpenAIClient] [] (decisions [] service))
        result (openai.decisions/create
                client {:model "gpt-5" :input "Classify this"
                        :questions [{:type :predicate :instructions "Is it safe?" :name "safe"}]})]
    (is (= "gpt-5" (.model ^DecisionCreateParams @captured)))
    (is (= {:model "gpt-5"
            :answers [{:type :predicate :name "safe" :probability 0.9}]
            :usage {:input-tokens 1 :input-tokens-details {:cached-tokens 0 :cache-write-tokens 0}
                    :output-tokens 2 :output-tokens-details {:reasoning-tokens 0} :total-tokens 3}}
           result))))

(deftest rejects-unknown-input-part-type
  (let [error (try
                (#'openai.decisions/->create-params
                 {:model "gpt-5"
                  :input [{:role :user :content [{:type :unknown}]}]
                  :questions [{:type :predicate :instructions "Is it safe?"}]})
                nil
                (catch clojure.lang.ExceptionInfo e e))]
    (is (= :unsupported-input-part (:openai/error (ex-data error))))))

(deftest rejects-missing-required-keys
  (let [error-data (fn [request]
                     (try
                       (#'openai.decisions/->create-params request)
                       nil
                       (catch clojure.lang.ExceptionInfo e (ex-data e))))]
    (is (= {:openai/error :missing-key :key :model}
           (error-data {:input "x" :questions []})))
    (is (= {:openai/error :missing-key :key :input}
           (error-data {:model "gpt-5" :questions []})))
    (is (= {:openai/error :missing-key :key :questions}
           (error-data {:model "gpt-5" :input "x"})))))
