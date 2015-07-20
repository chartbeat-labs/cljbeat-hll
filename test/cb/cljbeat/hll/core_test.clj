(ns cb.cljbeat.hll.core-test
  (:require [clojure.test :refer :all]
            [cb.cljbeat.hll.core :refer [get-hll]]))

(deftest test-hll
  (let [hll-0 (get-hll 0.01)
        hll-1 (.addItems hll-0 (map str (range 100)))
        hll-2 (.addItems hll-0 (map str (range 80 120)))
        hll-union (.unionHll hll-1 hll-2)
        hll-intersect (.intersectHll hll-1 hll-2)]
    (testing "Test intersection and union of hlls"
      (is (= 100 (.countDistinct hll-1)))
      (is (= 40 (.countDistinct hll-2)))
      (is (= 120 (.countDistinct hll-union)))
      (is (= 20 (.countDistinct hll-intersect))))
    (testing "Repeated inserts don't change"
      (is (= (.bin-vector hll-1)
             (.bin-vector (.addItems hll-1 (map str (range 100)))))))))

(defn calc-error-rate
  [target actual]
  (Math/abs (float (- 1 (/ target actual)))))

(deftest test-accuracy
  (let [empty-hll (get-hll 0.01)]
    (testing "Test empty hll"
      (is (= 0 (.countDistinct empty-hll))))
    (testing "Test low counts"
      (doseq [n (range 0 100)]
        (is (= n (.countDistinct (.addItems empty-hll (map str (range n))))))))
    (testing "Test accuracy"
      (doseq [error-rate [0.1 0.01 0.005]]
        (let [empty-hll (get-hll error-rate)]
          (doseq [n [1000 10000 100000 1000000]]
            (let [full-hll (.addItems empty-hll (map str (range n)))
                  estimate (.countDistinct full-hll)]
              (is (< (calc-error-rate n estimate)
                     ;; It's probabilistic... this should usually pass
                     (* 2 error-rate))))))))))
