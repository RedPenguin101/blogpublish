(ns blogformat.main
  (:require [hiccup.core :as hc]
            [hickory.core :as hk]))

(def newline? #{"\n" "\n  "})
(defn heading? [hic-el] (and (coll? hic-el) (#{:h1 :h2 :h3} (first hic-el))))
(def not-heading? (complement heading?))

(def t-section [:div.tufte-section])
(def main-text [:div.main-text])
(def sidenotes [:div.sidenotes])

(defn section-collect [hic]
  (conj t-section (into main-text hic) sidenotes))

(defn side-content? [hic] (some? (#{:figure :image} (first hic))))

(defn divide-content [hic]
  (let [content (group-by side-content? hic)]
    [(content false) (content true)]))

(defn section-collect-and-split [hic]
  (let [[main-content side-content] (divide-content hic)]
    (conj t-section (into main-text main-content) (into sidenotes side-content))))

(defn parse
  ([hic] (parse [] [hic] 0))
  ([completed [remaining] itbreak]
   (cond
     (> itbreak 100) :break
     (empty? remaining) completed
     :else (let [[pre-heading [heading & post-heading]] (split-with not-heading? remaining)
                 [section & from-next-head] (split-with not-heading? post-heading)]
             (recur (vec (remove empty? (conj completed pre-heading heading (section-collect-and-split section))))
                    from-next-head
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
          [:p {} "para4"]]))

(defn html-prep [html]
  (let [hiccup (hk/as-hiccup (hk/parse html))
        html-section (remove newline? (second hiccup))
        body (vec (remove newline? (nth html-section 3)))]
    {:html-start (vec (take 2 html-section))
     :head (vec (remove newline? (nth html-section 2)))
     :body-start (vec (take 2 body))
     :body-content (vec (drop 2 body))}))

(defn rewrite [html]
  (let [{:keys [html-start head body-start body-content]} (html-prep html)]
    (conj html-start head (into body-start (parse body-content)))))

(defn tufte-style [html-filename]
  (spit (str "rewrite_" html-filename) (hc/html (rewrite (slurp html-filename)))))

(comment
  (html-prep (slurp "text2.html"))

  (rewrite (slurp "text2.html"))

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