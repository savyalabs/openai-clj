(ns openai.impl-test
  (:require [clojure.test :refer [deftest is]]
            [openai.impl :as impl])
  (:import (com.openai.core Page)))

(defn- page [items next-page calls]
  (reify Page
    (hasNextPage [_] (boolean next-page))
    (nextPage [_]
      (swap! calls conj :next-page)
      next-page)
    (items [_]
      (swap! calls conj :items)
      items)))

(defn- page-chain [calls]
  (let [third (page [5 6] nil calls)
        second (page [3 4] third calls)]
    (page [1 2] second calls)))

(deftest lazy-pages-defers-page-traversal
  (let [calls (atom [])
        items (impl/lazy-pages (page-chain calls) {})]
    (is (= [] @calls))
    (is (= [1] (take 1 items)))
    (is (= [:items] @calls))
    (is (= [1 2 3 4 5 6] (vec items)))
    (is (= [:items :next-page :items :next-page :items] @calls))))

(deftest lazy-pages-max-items-bounds-consumption-independently
  (let [calls (atom [])
        items (impl/lazy-pages (page-chain calls) {:max-items 3})]
    (is (= [1 2 3] (vec items)))
    (is (= [:items :next-page :items] @calls))))

(deftest lazy-pages-max-pages-bounds-consumption-independently
  (let [calls (atom [])
        items (impl/lazy-pages (page-chain calls) {:max-pages 2})]
    (is (= [1 2 3 4] (vec items)))
    (is (= [:items :next-page :items] @calls))))

(deftest lazy-pages-zero-bounds-fetch-nothing
  (doseq [bound [:max-items :max-pages]]
    (let [calls (atom [])]
      (is (= [] (vec (impl/lazy-pages (page-chain calls) {bound 0}))))
      (is (= [] @calls)))))
