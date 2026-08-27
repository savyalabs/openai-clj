(ns openai.examples-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [examples.batch :as batch]
            [examples.realtime :as realtime]
            [examples.tool-calling :as tool-calling]))

(deftest cookbook-namespaces-load-without-calling-the-api
  (is (fn? realtime/run-session))
  (is (fn? tool-calling/run-tool-loop))
  (is (fn? batch/run-batch)))

(deftest client-owning-workflows-close-in-finally
  (doseq [path ["examples/tool_calling.clj" "examples/batch.clj"]]
    (let [source (slurp (io/resource path))]
      (is (re-find #"(?s)\(try.*\(finally\s+\(\.close client\)\)\)" source)
          (str path " must close its client in finally")))))
