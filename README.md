# cb.cljbeat.hll

Implementation of HyperLogLog based on
https://github.com/svpcom/hyperloglog/blob/master/hyperloglog/hll.py.

This project is a part of [cljbeat](http://chartbeat-labs.github.io/cljbeat/).

## Usage

```
(ns cb.cljbeat.hll.example
  (:require [cb.clbjeat.hll.core :as hll]))

(-> (hll/get-hll 0.01)
    (.addItems [1 2 3 4 5])
    (.countDistinct))  ;; Evaluates to 5
```

## Installation

With leiningen: `[com.chartbeat.cljbeat/hll "1.0.0"]`