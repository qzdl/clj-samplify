(ns clj-samplify.core-test
  (:require [clojure.test :refer :all]
            [clj-samplify.core :refer :all]
            [clj-samplify.scraper :refer :all]))

(deftest build-search_single-input []
  (testing "build-search with a single input"
    (is (= (str *base-url* "?q=+halftime")
           (build-search *base-url* ["halftime"])))))

(deftest build-search_n-inputs []
  (testing "build-search with a mutiple inputs"
    (is (= (str *base-url* "?q=+halftime+by+nas")
           (build-search *base-url* ["halftime" "by" "nas"])))))

;; (deftest a-test
;;   (testing "FIXME, I fail."
;;     (is (= 0 1))))
