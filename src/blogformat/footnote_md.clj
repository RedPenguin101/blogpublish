(ns blogformat.footnote-md
  (:require [clojure.string :as str]))

(def reference #"\[\^(\d+)\](?!:)")
(def reference-html "<sup>$1</sup>")

(def footnote #"(?m)^\[\^(\d+)\]:(.*)$")
(def footnote-html "<p class=\"footnote\">$1: $2</p>")

(str/replace "A footnote reference[^1]" reference reference-html)
(str/replace "[^1]: this is a footnote" reference reference-html)


(str/replace "A footnote reference[^1]" footnote footnote-html)
(str/replace "[^1]: this is a footnote" footnote footnote-html)

(-> (slurp "text4.md")
    (str/replace reference reference-html)
    (str/replace footnote footnote-html)
    (str/split-lines))

(defn prewrite-markdown [filename]
  (spit
   (str "rewrite_" filename)
   (-> (slurp filename)
       (str/replace reference reference-html)
       (str/replace footnote footnote-html))))

(prewrite-markdown "text4.md")