(ns clj-samplify.scraper-test
  (:require [clojure.test :refer :all]
            [clj-samplify.scraper :refer :all]))

(deftest build-search_single-input []
  (testing "build-search with a single input"
    (is (= (str *base-url* "?q=+halftime")
           (build-search *base-url* ["halftime"])))))

(deftest build-search_n-inputs []
  (testing "build-search with a mutiple inputs"
    (is (= (str *base-url* "?q=+halftime+by+nas")
           (build-search *base-url* ["halftime" "by" "nas"])))))

(deftest nsmap_1-arity
  (testing "nsmap single arity, namespace inner keys with outer keys"
    (is (= (nsmap {:links {:artist "la" :title "lt"}
                   :text  {:artist "ta" :title "tt"}})
           {:links/artist "la" :links/title "lt"
            :text/artist "ta"  :text/title "tt"}))))
