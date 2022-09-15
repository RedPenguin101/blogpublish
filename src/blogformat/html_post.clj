(ns blogformat.html-post
  (:require [clojure.walk :refer [postwalk]]
            [hiccup.core :as hc]
            [hickory.core :as hk]))

;; Parses HTML generated by pandoc etc. into hiccup, manipulates it.

(defn- footnote? [hic-el]
  (when (and (vector? hic-el) (= "footnote" (:class (second hic-el))))
    (:id (second hic-el))))

(defn- standalone-footnote? [hic-el]
  (and (vector? hic-el)
       (= :p (first hic-el))
       (footnote? (nth hic-el 2))))

(defn- footnote-ref? [hic-el]
  (when (and (vector? hic-el) (= "fnref" (:class (second hic-el))))
    (apply str (rest (:href (get-in hic-el [2 1]))))))

(defn- para-with-footnote-ref? [hic-el]
  (when (and (vector? hic-el) (= :p (first hic-el)))
    (some footnote-ref? hic-el)))

(defn- split-after [pred coll]
  (reduce (fn [A el]
            (if (pred el)
              (-> A (update (dec (count A)) conj el)
                  (conj []))
              (update A (dec (count A)) conj el)))
          [[]]
          coll))

(defn- replace-footnote [hic-el footnotes]
  (let [x (footnote-ref? (last hic-el))]
    (if x (conj (vec (remove footnote? hic-el)) (get footnotes x))
        (vec (remove footnote? hic-el)))))

(defn- restruct-fns [hic-el footnotes]
  (mapcat #(replace-footnote % footnotes) (split-after footnote-ref? hic-el)))

(defn- get-footnotes [hic-el]
  (cond (not (vector? hic-el)) []
        (footnote? hic-el) [[(footnote? hic-el) hic-el]]
        :else (mapcat get-footnotes hic-el)))

(defn- inline-footnotes
  "Given a hic-el containing footnotes, where the fnref
   and footnotes are separated, will reorg the datastructure
   such that the footnote immediately follows the fnref"
  [hic]
  (let [footnotes (into {} (get-footnotes hic))]
    (vec (postwalk (fn [el]
                     (cond (para-with-footnote-ref? el)
                           (vec (restruct-fns el footnotes))
                           (standalone-footnote? el) nil
                           :else el))
                   hic))))

(defn- html-prep [html]
  (let [hiccup (hk/as-hiccup (hk/parse html))]
    (first (rest hiccup))))

(defn rewrite [html]
  (-> html
      html-prep
      inline-footnotes
      hc/html))

(comment
  ;; This NS contains functions for rewriting html.
  ;; the public function of this ns is `rewrite`. This is just an orchastration wrapper
  ;; around the transformations. It parses the html to hiccup, runs the transformations,
  ;; then outputs an html string.

  ;; Currently the only transformation applies is to `inline-footnotes`.

  ;; A "para-with-footnote-ref" - a hiccup data structure where the top level element
  ;; is a para, and the para contains a footnote reference.
  ;; Notice that the footnote (span.footnote) is not next to the thing that references
  ;; it (sup.fnref)
  ;; In our output document, we want the footnote to be horizontally situated in the 
  ;; right gutter of the page (which can be accompished with CSS)
  ;; and vertically situated as closely as possible to the reference to it.
  ;; To do this, we need to restructure the html, which is the purpose of this namespace.
  (def sample
    [:p {}
     "Text"
     [:sup {:class "fnref"} [:a {:id "note2", :href "#fn2", :title "Footnote 2"} "2"]]
     "More text"
     [:span {:id "fn2", :class "footnote"}
      [:a {:class "fnref", :href "#note2", :title "Footnote 2 Reference"} "2"]
      "Footnote text"]])

  ;; The main function of this ns, inline-footnotes, does this.
  ;; Note in the below evaluation how the span.footnote now follows immediately
  ;; after the sup.fnref. This will achieve the desired result.
  (inline-footnotes sample)
  ;; => 
  [:p
   {}
   "Text"
   [:sup {:class "fnref"} [:a {:id "note2", :href "#fn2", :title "Footnote 2"} "2"]]
   [:span
    {:id "fn2", :class "footnote"}
    [:a {:class "fnref", :href "#note2", :title "Footnote 2 Reference"} "2"]
    "Footnote text"]
   "More text"]

  ;; An expended sample, representing the body of an actual html document.
  ;; Notably, the footnote itself is not a sub-element of the para containing 
  ;; the reference to it. This is more likely to be the case than the above sample,
  ;; where the footnote is a child of the para.
  (def sample-standalone-fn
    [:body
     {}
     [:h1 {:id "the-title"} "The title"]
     [:p
      {}
      "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse quis lacinia est. Curabitur posuere, orci sit amet dapibus placerat, nibh erat efficitur quam, nec iaculis est augue a diam. Curabitur euismod"
      [:sup {:class "fnref"} [:a {:id "note1", :href "#fn1", :title "Footnote 1"} "1"]]
      ", libero ut pharetra varius, massa enim imperdiet felis, sed gravida ipsum felis quis tellus. Ut facilisis efficitur risus, sed tristique nisi tempor vel. Nunc leo libero, elementum ut arcu at, consequat tincidunt lorem. Praesent ipsum ante, sagittis non blandit a, condimentum ut felis. Aliquam erat volutpat. Cras faucibus massa eget nisl feugiat, et posuere turpis semper. Phasellus mattis id nisi at volutpat."]
     [:p
      {}
      [:span
       {:id "fn1", :class "footnote"}
       " "
       [:a {:class "fnref", :href "#note1", :title "Footnote 1 Reference"} "1"]
       " This is a footnote "]]])

  ;; The four predicate functions read a hiccup data structure, and if the structure
  ;; contains at least one of type of thing it's looking for, it returns the name of
  ;; the first one it finds. 
  ;; Note that the name returned of the ref and the footnote it references are the same
  (footnote-ref? (nth sample 3))
  ;; => "fn2"
  (footnote? (nth sample 5))
  ;; => "fn2"
  (para-with-footnote-ref? sample)
  ;; => "fn2"
  (standalone-footnote? (nth sample-standalone-fn 4))
  ;; => "fn1"

  ;; split-after is a utility function that splits a sequence into sub-sequences
  ;; after it encounters seomthing that matches the predicate
  (split-after number? [:a :b :c 1 :d :e 2])
  ;; => [[:a :b :c 1] [:d :e 2] []]

  (split-after footnote-ref? sample)

  ;; get-footnotes parses an html tree and returns a dictionary of all the footnotes
  ;; (not the footnote _references_) in it. This is used as a lookup when restructuring
  ;; the document.
  (into {} (get-footnotes sample))
  ;; => 
  {"fn2" [:span
          {:id "fn2", :class "footnote"}
          [:a {:class "fnref", :href "#note2", :title "Footnote 2 Reference"} "2"]
          "Footnote text"]})

