(ns sha224rch.core
  (:gen-class)
  (:import [java.io File]
           [java.nio.file Files LinkOption]
           [java.nio.file.attribute BasicFileAttributes])
  (:require [clojure.data.xml :as xml]
   [clojure.java.io :refer [as-file copy make-parents]]
            [clojure.string :refer [join]]
            [digest :refer [sha-224]]
            [robert.hooke :refer [add-hook]]))

(defn ^:private create-fileinfo [file target-dir]
  (let [checksum (sha-224 file)
        subpath (loop [partitions [3 3 3 4 5 6 8 10 14], val checksum, res []]
                  (if-let [cur-partition (first partitions)]
                    (recur (rest partitions)
                           (drop cur-partition val)
                           (conj res (apply str (take cur-partition val))))
                    (join "/" res)))

        dir-path (join "/" [target-dir subpath])]
    {:entry file
     :checksum checksum
     :subpath subpath
     :dir-path dir-path
     :temp-path (str dir-path ".tmp")
     :meta-path (str dir-path ".xml")
     :file-exists (.exists (as-file dir-path))}))

(defn write-metadata [{:keys [entry meta-path]}]
  (let [last-modified (.lastModified entry)
        attr (Files/readAttributes (.toPath entry) BasicFileAttributes
                                   (into-array LinkOption []))
        tags (xml/element
              :sha244rch-metadata
              {:version "1"
               :filename (.getPath entry)
               :creation-time (.creationTime attr)
               :modification-time (.lastModifiedTime attr)})]
    (with-open [out-file (clojure.java.io/writer meta-path :encoding "UTF-8")]
      (xml/emit tags out-file))))

(defn ^:private copy-file [{:keys [dir-path temp-path checksum entry] :as orig-arg}]
  (make-parents dir-path)
  (copy entry (as-file temp-path))
  (write-metadata orig-arg)
  (.renameTo (File. temp-path) (File. dir-path)))

(defn archive-dir [source-dir target-dir filter]
  (let [source (file-seq (clojure.java.io/file source-dir))]
    (loop [infiles source, copied 0, total 0]
      (if-let [entry (first infiles)]
        (if (and (.isFile entry) (filter entry))
          (let [{:keys [checksum subpath dir-path file-exists meta-path] :as fileinf}
                (create-fileinfo entry target-dir)]
            (if file-exists
              (when-not (.exists (as-file meta-path))
                (write-metadata fileinf))
              (copy-file fileinf))
            (recur (rest infiles)
                   (if file-exists copied (inc copied))
                   (inc total)))
          (recur (rest infiles) copied total))
        {:copied copied, :duplicates (- total copied)}))))

(defn ^:private copy-file-hook [f {:keys [checksum entry] :as orig-arg}]
  (print (format  "copy      %s %s" checksum (.getPath entry)))
  (f orig-arg)
  (println "."))

(add-hook #'copy-file #'copy-file-hook)

(defn -main [& args]
  (if (< (count args) 2)
    (println "Copies files from source to target using SHA-224 to build the target directory structure.

parameters: source target [pattern]

Example: /srv/files /backup \"(?i)(jpe?g$|png$|gif$|tiff?$|bmp$|mov$|mp4$|avi$|mkv$|mpeg$|webm$|wmv$)\"")
    (let [source (nth args 0)
          target (nth args 1)
          pattern (if (> (count args) 2) (nth args 2) ".*")]
      (println (archive-dir
                source target
                #(re-find (re-pattern pattern) (.getPath %)))))))
