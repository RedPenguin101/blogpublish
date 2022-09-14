(ns blogformat.html-post
  (:require [clojure.walk :refer [postwalk]]
            [clojure.string :as str]
            [hiccup.core :as hc]
            [hickory.core :as hk]))

;; Parses HTML generated by pandoc etc. into hiccup, manipulates it.

(defn footnote? [hic-el]
  (when (and (vector? hic-el) (= "footnote" (:class (second hic-el))))
    (:id (second hic-el))))

(defn standalone-footnote? [hic-el]
  (def debug hic-el)
  (and (vector? hic-el)
       (= :p (first hic-el))
       (footnote? (nth hic-el 2))))

(defn footnote-ref? [hic-el]
  (when (and (vector? hic-el) (= "fnref" (:class (second hic-el))))
    (apply str (rest (:href (get-in hic-el [2 1]))))))

(defn para-with-footnotes? [hic-el]
  (when (and (vector? hic-el) (= :p (first hic-el)))
    (some footnote-ref? hic-el)))

(def sample
  [:p {} "Text"
   [:sup {:class "fnref"} [:a {:id "note2", :href "#fn2", :title "Footnote 2"} "2"]]
   "More text"
   [:span {:id "fn2", :class "footnote"}
    [:a {:class "fnref", :href "#note2", :title "Footnote 2 Reference"} "2"]
    "Footnote text"]])

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

(footnote-ref? (nth sample 3))
(footnote? (nth sample 5))
(para-with-footnotes? sample)
(standalone-footnote? (nth sample-standalone-fn 4))

(defn split-after [pred coll]
  (reduce (fn [A el]
            (if (pred el)
              (-> A (update (dec (count A)) conj el)
                  (conj []))
              (update A (dec (count A)) conj el)))
          [[]]
          coll))

(split-after number? [:a :b :c 1 :d :e 2])
(split-after footnote-ref? sample)

(defn f [hic-el footnotes]
  (let [x (footnote-ref? (last hic-el))]
    (if x (conj (vec (remove footnote? hic-el)) (get footnotes x))
        (vec (remove footnote? hic-el)))))

(defn restruct-fns [hic-el footnotes]
  (mapcat #(f % footnotes) (split-after footnote-ref? hic-el)))

(defn get-footnotes [hic-el]
  (cond (not (vector? hic-el)) []
        (footnote? hic-el) [[(footnote? hic-el) hic-el]]
        :else (mapcat get-footnotes hic-el)))

(into {} (get-footnotes sample))

(let [fns (into {} (get-footnotes sample))]
  (restruct-fns sample fns))

(map footnote-ref? sample)

(restruct-fns sample {})

(defn inline-footnotes
  "Given a hic-el containing footnotes, where the fnref
   and footnotes are separated, will reorg the datastructure
   such that the footnote immediately follows the fnref"
  [hic]
  (let [footnotes (into {} (get-footnotes hic))]
    (vec (postwalk (fn [el]
                     (cond (para-with-footnotes? el)
                           (vec (restruct-fns el footnotes))
                           (standalone-footnote? el) nil
                           :else el))
                   hic))))

(inline-footnotes sample)
(inline-footnotes (into [:body
                         {}
                         [:h1 {:id "the-title"} "The title"]]
                        [sample]))

(defn html-prep [html]
  (let [hiccup (hk/as-hiccup (hk/parse (str/replace html #"\n" "")))]
    (first (rest hiccup))))

(defn rewrite [html]
  (-> html
      html-prep
      inline-footnotes
      hc/html))



(comment
  (count (html-prep (slurp "markdown/posts/text4_preproc.html")))
  (first (html-prep (slurp "markdown/posts/text4_preproc.html")))

  (spit
   "markdown/posts/text4_pre_and_postproc.html"
   (rewrite (slurp "markdown/posts/text4_preproc.html")))



  (spit "markdown/posts/text3_pre_and_postproc.html"
        (-> (slurp "markdown/posts/text3_preproc.html")
            html-prep
            inline-footnotes
            hc/html))
  (standalone-footnote? debug))
