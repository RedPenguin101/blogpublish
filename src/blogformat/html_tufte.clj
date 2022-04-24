(ns blogformat.html-tufte
  (:require [hiccup.core :as hc]
            [hickory.core :as hk]))


(def newline? #{"\n" "\n  "})

;; HTML element type matching

(defn is-html-type [tags] (fn [hic] (and (coll? hic) (tags (first hic)))))
(defn is-class? [class] (fn [hic] (= class (:class (second hic)))))
(def heading? (is-html-type #{:h1 :h2 :h3}))
(def footnote? (every-pred (is-html-type #{:p}) (is-class? "footnote")))

(def tufte-section? (complement heading?))

(def t-section [:div.tufte-section])
(def main-text [:div.main-text])
(def sidenotes [:div.sidenotes])

(def side-content? (comp boolean (some-fn (is-html-type #{:figure :image}) footnote?)))

(defn divide-content [hic]
  (let [content (group-by side-content? hic)]
    [(content false) (content true)]))

(defn section-collect-and-split [hic]
  (let [[main-content side-content] (divide-content hic)]
    (conj t-section (into main-text main-content) (into sidenotes side-content))))

(defn parse
  ([hic] (parse [] hic 0))
  ([completed remaining itbreak]
   (cond
     (> itbreak 100) :break
     (empty? remaining) completed
     (= 1 (count remaining)) (into completed remaining)
     :else (let [[pre-heading [heading & post-heading]] (split-with tufte-section? remaining)
                 [section & from-next-head] (split-with tufte-section? post-heading)]
             (recur (vec (remove empty? (conj completed pre-heading heading (section-collect-and-split section))))
                    (first from-next-head)
                    (inc itbreak))))))

(comment
  (parse [[:h1 {:id "the-title"} "The title"]
          [:p {} "para1"]])

  (parse [])

  (parse [[:h1 {:id "the-title"} "The title"]
          [:p {} "para1"]
          [:p {} "para2"]])

  (parse [[:h1 {:id "the-title"} "The title"]
          [:p {} "para1"]
          [:p {} "para2"]
          [:h2 "blah"]
          [:p {} "para3"]
          [:p {} "para4"]])

  (parse [[:h1 {:id "the-title"} "The title"]
          [:p {} "para1"]
          [:p {} "para2"]
          [:h2 "blah"]
          [:p {} "para3"]
          [:p {} "para4"]
          [:h2 "this has no text after it - corner case"]])

  (parse [[:h1 {:id "the-title"} "The title"]
          [:p {} "para1"]
          [:figure]
          [:p {} "para2"]
          [:h2 "blah"]
          [:p {} "para3"]
          [:image]
          [:p {} "para4"]
          [:h2 "this has no text after it - corner case"]])

  (parse [[:h1 {:id "the-title"} "The title"]
          [:p {} "para1"]
          [:figure]
          [:p {} "para2"]
          [:p {:class "footnote"}]
          [:h2 "blah"]
          [:p {} "para3"]
          [:image]
          [:p {} "para4"]
          [:h2 "this has no text after it - corner case"]]))

(defn discard-doc
  "Aims to make HTML the first tag of the hic structure"
  [hic]
  (first (if (= "<!DOCTYPE html>" (first hic)) (rest hic) hic)))

(defn html-prep [html]
  (let [hiccup (remove newline? (discard-doc (hk/as-hiccup (hk/parse html))))
        body (vec (remove newline? (nth hiccup 3)))]
    {:html-start (vec (take 2 hiccup))
     :head (vec (remove newline? (nth hiccup 2)))
     :body-start (vec (take 2 body))
     :body-content (vec (drop 2 body))}))

(defn rewrite [html]
  (let [{:keys [html-start head body-start body-content]} (html-prep html)]
    (conj html-start head (into body-start (parse body-content)))))

(defn tufte-style [html-in]
  (hc/html (rewrite html-in)))

(comment
  (html-prep (slurp "text2.html"))

  (rewrite (slurp "text2.html"))
  (rewrite (slurp "text3.html"))

  (hc/html (rewrite (slurp "text2.html")))

  (spit "text2_rewritten.html" (hc/html (rewrite (slurp "text2.html"))))

  (tufte-style "text.html")
  (tufte-style "text2.html")
  (tufte-style "text3.html"))

;; pullng out side content

(comment
  (def test-hiccup (take 3 (drop 3 (:body-content (html-prep (slurp "text3.html"))))))

  (divide-content test-hiccup)

  (section-collect-and-split test-hiccup)

  (tufte-style "text2.html")
  (tufte-style "text3.html"))

;; handling footnotes

(comment
  (def test-hiccup2 (:body-content (html-prep (slurp "text4.html"))))

  test-hiccup2
  (tufte-style "text.html")
  (tufte-style "text2.html")
  (tufte-style "text3.html")
  (tufte-style "text4.html"))