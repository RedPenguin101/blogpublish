(ns blogformat.main
  (:gen-class)
  (:require [blogformat.footnote-md :as md]
            [blogformat.html-post :as html-post]
            [hiccup.core :refer [html]]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))


(def index "./index.html")
(def books "./books.html")

(def post-paths {:html     "./html/posts/"
                 :markdown "./markdown/posts/"})

(def book-paths {:html     "./html/books/"
                 :markdown "./markdown/books/"})

(def css-path "../../css/style.css")

;;;;;;;;;;;;;;;;;;;;
;; file operations
;;;;;;;;;;;;;;;;;;;;

(defn get-file-paths
  "Returns a list of all files in a folder"
  [folder-path]
  (mapv #(str folder-path %) (str/split-lines (:out (sh "ls" folder-path)))))

(defn file-first-line [file-path]
  (with-open [rdr (io/reader file-path)]
    (first (line-seq rdr))))

(defn move-file [in-path out-folder]
  (sh "mv" in-path out-folder)
  in-path)

;;;;;;;;;;;;;;;;;;;;
;; Post publishing
;;;;;;;;;;;;;;;;;;;;

(defn preproc-markdown [file-path]
  (let [out-file-path (str/replace file-path ".md" ".temp")]
    (spit out-file-path (md/prewrite-markdown (slurp file-path)))
    out-file-path))

(defn publish-markdown [file-path]
  (sh "pandoc"
      file-path "-f" "markdown" "-t" "html" "-s" "-c" css-path
      "-o" (str/replace file-path ".temp" ".html"))
  (str/replace file-path ".temp" ".html"))

(defn postproc-markdown [file-path]
  (spit file-path (html-post/rewrite (slurp file-path)))
  file-path)

(defn cleanup [file-path]
  (sh "rm" (str/replace file-path ".html" ".temp")))

(defn publish! [pub-fn pre-fn post-fn in-folder out-folder]
  (->> (get-file-paths in-folder)
       (map pre-fn)
       (map pub-fn)
       (map post-fn)
       (map #(move-file % out-folder))
       (map cleanup)
       doall))

(comment
  ;; For testing individual files
  ;; (This should probably be more like what the program does TBH)
  (-> "markdown/posts/text5.md"
      preproc-markdown
      publish-markdown
      postproc-markdown))

;;;;;;;;;;;;;;;;;;;;
;; Index building
;;;;;;;;;;;;;;;;;;;;

(defn post-title [file-path]
  (subs (file-first-line file-path) 2))

(defn path-from-html
  "When given a path to an html file, will attempt
  to find and return the path to the corresponding
  mardown or adoc file."
  [file-path extension folder-paths]
  (str (extension folder-paths)
       (str/replace (last (str/split file-path #"/"))
                    ".html"
                    ".md")))

(defn get-title [file-path folder-paths]
  (post-title (path-from-html file-path :markdown folder-paths)))

(defn entry-from-post [raw-paths file-path]
  (let [filename (last (str/split file-path #"/"))
        [y m d] (re-seq #"\d+" filename)]
    {:date (str/join "-" [y m d])
     :filename filename
     :title (get-title file-path raw-paths)}))

(defn build-index [entries]
  [:html
   [:head
    [:title "Joe's Blog"]
    [:link {:rel "icon" :type "image/x-icon" :href "./favicon.ico"}]
    [:link {:rel "stylesheet" :href "./css/style.css"}]]
   [:body
    [:div
     [:h1 "Joe's Blog"]
     [:h2 "Other stuff"]
     [:ul [:li [:a {:href books} "Notes on books"]]]
     [:h2 "Blog posts"]
     [:table
      [:tr [:th "Date"] [:th "Title"]]
      (for [entry (reverse (sort-by :date entries))]
        [:tr
         [:td (:date entry)]
         [:td [:a {:href (str (:html post-paths) (:filename entry))}
               (:title entry)]]])]]]])

(defn create-post-index! []
  (->> (get-file-paths (:html post-paths))
       (map #(entry-from-post post-paths %))
       build-index
       html
       (spit index)))

(defn entry-from-book [raw-paths file-path]
  (let [filename (last (str/split file-path #"/"))]
    {:filename filename
     :title (get-title file-path raw-paths)}))

(defn book-index [entries]
  [:html
   [:head
    [:title "Book Notes"]
    [:link {:rel "icon" :type "image/x-icon" :href "./favicon.ico"}]
    [:link {:rel "stylesheet" :href "./css/style.css"}]]
   [:body
    [:div
     [:h1 "Books"]
     [:ul
      (for [entry (sort-by :title entries)]
        [:li [:a {:href (str (:html book-paths) (:filename entry))}
              (:title entry)]])]]]])

(defn create-book-index! []
  (->> (get-file-paths (:html book-paths))
       (map #(entry-from-book book-paths %))
       book-index
       html
       (spit books)))

(defn -main []
  (println "Publishing markdown Posts")
  (publish! publish-markdown preproc-markdown postproc-markdown (:markdown post-paths) (:html post-paths))
  (println "Publishing markdown Books")
  (publish! publish-markdown preproc-markdown postproc-markdown (:markdown book-paths) (:html book-paths))
  (println "Creating Post index")
  (create-post-index!)
  (println "Creating Book Index")
  (create-book-index!)
  (println "DONE")
  (shutdown-agents))

(comment
  (-main))
