(ns blogformat.footnote-md
  (:require [clojure.string :as str]))

(def reference #"\[\^(\d+)\](?!:)")
(def reference-html "<sup class=fnref><a id=\"note$1\" href=\"#fn$1\" title=\"Footnote $1\">$1</a></sup>")

(def footnote #"(?m)^\[\^(\d+)\]:(.*)$")
(def footnote-html
  "<span id=fn$1 class=\"footnote\">
  <a class=\"fnref\" href=\"#note$1\" title=\"Footnote $1 Reference\">$1</a>
  $2
</span>")

(comment
  (str/replace "A footnote reference[^1]" reference reference-html)
  ;; => "A footnote reference<sup class=fnref><a id=\"note1\" href=\"#fn1\" title=\"Footnote 1\">1</a></sup>"

  (str/replace "[^1]: this is a footnote" footnote footnote-html)
  ;; => "<span id=fn1 class=\"footnote\">
  ;;       <a class=\"fnref\" href=\"#note1\" title=\"Footnote 1 Reference\">1</a>
  ;;         this is a footnote
  ;;     </span>"


  (spit "markdown/posts/text3_preproc.md"
        (-> (slurp "markdown/posts/text3.md")
            (str/replace reference reference-html)
            (str/replace footnote footnote-html)
            #_(str/split-lines))))

(defn prewrite-markdown [text]
  (-> text
      (str/replace reference reference-html)
      (str/replace footnote footnote-html)))
