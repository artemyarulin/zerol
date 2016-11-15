(ns zerol.engine-test
  (:require [zerol.engine :as z]
            [clojure.test :refer [deftest is are]]))

(deftest normalize-query
  (are [exp query] (= exp (z/normalize-query query))
    [[:ui]] [:ui]
    [[:ui :data]] [:ui :data]
    [[:ui :a] [:data :b]] [[:ui :a][:data :b]]))

(deftest normalize-rule
  (are [exp rule] (= exp (z/normalize-rule rule))
    [[[[:ui]] [[:ui]] identity]] [[:ui] [:ui] identity]
    [[[[:a]] [[:b]] identity] [[[:c]] [[:z]] identity]]
    (fn[][[[:a][:b] identity] [[:c][:z] identity]])))

;; TODO: Add following tests as well
;; (is (z/normalize-rules [[[:ui] [:ui] identity]]))
;; ;; Yes, notify up
;; ;; :a:z -> :a:x
;; (z/normalize-rules [[[:a :z] [:a :x] nil]])
;; ;; :a -> :a
;; (z/normalize-rules [[[:a] [:a] nil]])
;; ;; :a:z -> :a, :a -> :a:z
;; (z/normalize-rules [[[:a :z] [:a] nil]])
;; ;; :a -> :a:z, :a:z -> :a
;; (z/normalize-rules [[[:a] [:a :z] -]])
;; (is (z/normalize-rules [[[:ui :counter] [:ui :counter] nil]
;;                         [[:ui] [:ui] nil]]))
;;(is (z/normalize-rules [[[:ui :counter :value] [:ui :counter] nil]])))
(deftest normalize-rules
  (are [rules exp] (= exp (z/normalize-rules rules))
    [[[:a] [:b] identity]] {[:a] [[[[:a]] [[:b]] identity]]
                            [:b] []}
    [[[:a :c] [:a :z] identity]] {[:a :c] [[[[:a :c]] [[:a :z]] identity]]
                                  [:a :z] []}
    [[[[:a] [:c]] [[:a] [:c]] identity]] {[:a] [[[[:a] [:c]] [[:a] [:c]] identity]]
                                          [:c] [[[[:a] [:c]] [[:a] [:c]] identity]]}
    [[[:a :c :d] [:a :c :z] identity]] {[:a :c :d] [[[[:a :c :d]] [[:a :c :z]] identity]]
                                        [:a :c :z] []}))

(deftest apply-changes
  (let [cancel (atom nil)]
    (is (= {:io {:hello nil}}
           (z/apply-changes {:io {:hello {:type :http :cancel #(reset! cancel %)}}}
                            [[:io :hello]]
                            [nil])))
    (is (= {:type :http} @cancel))))

(deftest engine
  (is (= {:state {:a 1, :b 2}
          :rules {[:a] [[[[:a]] [[:b]] inc]]
                  [:b] []}
          :renderer nil}
         @(z/engine [[[:a][:b] inc]]
                   {:a 1}))))

(deftest apply-changes
  (is (= {:a 3 :b 4}
         (z/apply-changes {:a 1 :b 2}
                          [[:a] [:b]]
                          [3 4]))))

(deftest balance
  (let [rules (z/normalize-rules [[[:a :z] [:b :z] inc]])]
    (is (= {:a {:z 10}} (z/balance {:a {:z 10}} rules [])))
    (is (= {:a {:z 10} :b {:z 11}} (z/balance {:a {:z 10}} rules [[:a :z]])))
    (comment (is (= {:a {:z 10} :b {:z 11}} (z/balance {:a {:z 10}} rules [[:a]]))))))

(deftest engine-io
  (-> (z/engine [[[:update] [:io :updating] #(hash-map :type :http :value %)]
                 [[:io :updating :result] [:data :updated] identity]]
                {:update 2}
                #(assoc-in %2 [:updating :result] 42))
      deref
      (get-in [:state :data :updated])
      (= 42)
      is))

(deftest engine-async-io
  (let [p (promise)]
    (z/engine [[[:update] [:io :updating] #(hash-map :type :http :value %)]
               [[:io :updating :result] [:data :updated]
                (fn[value]
                  (when value
                    (deliver p value))
                  value)]]
              {:update 2}
              (fn[event! io] (when-not (-> io :updating :result)
                              (future
                                (Thread/sleep 1000)
                                (event! [:io :updating] [:io :updating :result] (constantly 42))))
                io))
    (is (= 42 @p))))

(deftest balance-one-rule-start
  (-> (z/engine [[[:a] [:b] inc]
                 [[:b] [:c] inc]]
                {:a 0})
      (z/apply-event! [:a] [:a] (constantly 10))
      :state
      (= {:a 10 :b 11 :c 12})
      is))
