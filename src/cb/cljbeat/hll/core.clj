(ns cb.cljbeat.hll.core
  (:gen-class)
  (:require [cb.cljbeat.hll.constants :as const]
            [clojure.math.numeric-tower :as math]
            [pandect.core :as pan]))

(defn- log2
  "log base 2."
  [n]
  (/ (Math/log n) (Math/log 2)))

;;; cardinality estimate utilities
(defn- index-bits->num-bins
  "Calculate the number of bins associated to the given
  number of desired bits."
  [index-bits]
  (bit-shift-left 1 index-bits))

(defn- error-rate->index-bits
  "Determine the quantity of bins (in bits) required for an hll
  to give the desired precision."
  [error-rate]
  {:post [(<= 4 % 16)]}
  (int (math/ceil (* (log2 (/ 1.04 error-rate)) 2))))

(defn get-cardinality-correction
  "Get correction value used to calculate hll cardinality."
  [index-bits]
  {:pre [(<= 4 index-bits 16)]}
  (case index-bits
    4 0.673
    5 0.697
    6 0.709
    (/ 0.7213 (+ 1.0 (/ 1.079 (index-bits->num-bins index-bits))))))

(defn- get-nearest-neighbors
  "Get indexes of nearest elements within estimate vector."
  [estimate estimate-vector]
  (let [distance-function (fn [idx value] [(math/expt (- estimate (double value)) 2) idx])
        distance-map (sort (vec (map-indexed distance-function estimate-vector)))
        indices (for [[dist idx] (take 6 distance-map)] idx)]
    indices))

(defn- get-bias-correction
  "Calculate bias correction associated to the given estimate"
  [estimate index-bits]
  (let [bias-vector (get const/bias-data (- index-bits  4))
        estimate-vector (get const/raw-estimate-data (- index-bits  4))
        nearest-neighbors (get-nearest-neighbors estimate estimate-vector)
        bias-sum (reduce + (map #(get bias-vector %) nearest-neighbors))
        bias-correction (/ bias-sum (count nearest-neighbors))]
    bias-correction))

;;; Hashing and HLL indexing utilities
(defn- str->hash
  "Use sha1 to hash a string to long"
  [s]
  (BigInteger. (subs (pan/sha1 s) 0 16) 16))

(defn hash->index
  "Calculate which index out of num-bins this hash belongs to."
  [hash-item num-bins]
  (long (.and hash-item (BigInteger. (str (- num-bins 1))))))

(defn- num-leading-zeros
  "Calculate the number leading zeros in front of hash-item
  after the first index-bits have been truncated"
  [hash-item index-bits]
  (let [w (long (.shiftRight hash-item (BigInteger. (str index-bits))))
        max-width (- 64 index-bits )]
    (inc (- max-width (count (Long/toBinaryString w))))))

(defn- item->index-num-leading-zeros-pair
  "Calculate hll bucket and number of leading zeros
  for hash of the string item."
  [item index-bits]
  (let [hash-item (str->hash item)
        num-bins (index-bits->num-bins index-bits)]
    [(hash->index hash-item num-bins)
     (num-leading-zeros hash-item index-bits)]))

;;; hll update utility
(defn- update-bin-vector-with-items
  "Update hll bin-vector with new items"
  [items index-bits bin-vector]
  (->> items
    (map #(item->index-num-leading-zeros-pair % index-bits))
    (reduce (fn [m-idx [idx v]] (assoc m-idx idx (max (m-idx idx) v))) bin-vector)))

;;; hll "interface"
(defprotocol HyperLogLogInterface
  (addItems [this items])
  (unionHll [this input-hll])
  (intersectHll [this input-hll])
  (estimateCardinality [this])
  (linearCount [this])
  (countDistinct [this]))
  ; (packHll [this])
  ; (unpackHll [this packed-data]))

;;; hll "class"
(defrecord HyperLogLog [index-bits num-bins bin-vector]
  HyperLogLogInterface

  (addItems [this items]
    "Add a list of strings to this hll."
    (let [new-bin-vector (update-bin-vector-with-items items index-bits bin-vector)]
      (HyperLogLog. index-bits num-bins new-bin-vector)))

  (unionHll [this input-hll]
    "Union this hll with another hll."
    (let [input-index-bits (:index-bits input-hll)
          input-bin-vector (:bin-vector input-hll)]
      (assert (== index-bits input-index-bits))
      ;; Define a new hll with max of bin-vector's
      (HyperLogLog. index-bits num-bins (vec (map max bin-vector input-bin-vector)))))

  (intersectHll [this input-hll]
    "Intersect this hll with another hll."
    (let [input-index-bits (:index-bits input-hll)
          input-bin-vector (:bin-vector input-hll)]
      (assert (== index-bits input-index-bits))
      ;; Define a new hll with min of bin-vector's
      (HyperLogLog. index-bits num-bins (vec (map min bin-vector input-bin-vector)))))

  (estimateCardinality [this]
    "Calculate an hll cardinality estimate."
    (let [power-sum (reduce + (map #(math/expt 2. (- %)) bin-vector))
          card-correction (get-cardinality-correction index-bits)
          estimate (/ (* card-correction num-bins num-bins) power-sum)
          bias-correction (get-bias-correction estimate index-bits )
          biased-estimate (- estimate bias-correction)]
      (if (<= estimate (* 5 num-bins))
        biased-estimate
        estimate)))

  (linearCount [this]
    "In the case of small cardinalities estimate cardinality
    from the number of bins which have been updated."
    (let [empty-count (count (filter #(== % 0) bin-vector))]
      (if (zero? empty-count)
        nil  ;; if all buckets have been updated, linearcount isn't useful
        (* num-bins (Math/log (/ num-bins empty-count))))))

  (countDistinct [this]
    "Estimate a distinct count by using either a linear count
    or an hll cardinality estimate."
    (math/round
     (let [linear-count (linearCount this)]
       (if (nil? linear-count)
         (estimateCardinality this)
         (let [threshold (get const/threshold-data
                              (- index-bits 4))]
           (if (<= linear-count threshold)
             linear-count
             (estimateCardinality this))))))))

(defn init-hll
  "Initialize an hll."
  [index-bits num-bins bin-vector]
  (HyperLogLog. index-bits num-bins bin-vector))

(defn get-hll
  "Initialize an hll of desired error rate."
  [error-rate]
  {:pre [(and "Only supports between 4 and 2^16 buckets"
              (>= 0.3676 error-rate 0.00407))]}
  (let [index-bits (error-rate->index-bits error-rate)
        num-bins (index-bits->num-bins index-bits)
        bin-vector (vec (repeat num-bins 0))]
    (init-hll index-bits num-bins bin-vector)))
