(ns metabase.util.export
  (:require [cheshire.core :as json]
            [clojure.data.csv :as csv]
            [clojure.tools.logging :as log]
            [metabase.api.common :as api]
            [metabase.public-settings :as public-settings]
            [metabase.api.common :refer [*current-user*]]
            [metabase.util.date :as df]
            [dk.ative.docjure.spreadsheet :as spreadsheet])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream File]
            (java.io ByteArrayOutputStream FileInputStream)
            (javax.imageio ImageIO)
            (org.apache.poi.xssf.usermodel XSSFRelation)
            (java.awt.image BufferedImage)
            (java.awt Transparency Color Font Graphics2D RenderingHints)
            (org.apache.poi.ss.usermodel Workbook Sheet Cell Row)
            (org.apache.poi.ss.util CellReference AreaReference)))

;; add a generic implementation for the method that writes values to XLSX cells that just piggybacks off the
;; implementations we've already defined for encoding things as JSON. These implementations live in
;; `metabase.middleware`.
(defmethod spreadsheet/set-cell! Object [^Cell cell, value]
  (when (= (.getCellType cell) Cell/CELL_TYPE_FORMULA)
    (.setCellType cell Cell/CELL_TYPE_STRING))
  ;; stick the object in a JSON map and encode it, which will force conversion to a string. Then unparse that JSON and
  ;; use the resulting value as the cell's new String value.  There might be some more efficient way of doing this but
  ;; I'm not sure what it is.
  (.setCellValue cell (str (-> (json/generate-string {:v value})
                               (json/parse-string keyword)
                               :v))))

(defn generate-watermark-image
  "generate watermark image with the defined content"
  [^String current-user ^Boolean needDateTime]
  (prn "current-user is :" current-user)
  (let [width 300
        height 100
        font (Font. "microsoft-yahei" Font/PLAIN 20)
        image (BufferedImage. width height (BufferedImage/TYPE_INT_ARGB_PRE))
        graphics2D (.createGraphics image)
        ;image = graphics2D.getDeviceConfiguration().createCompatibleImage(width, height, Transparency.TRANSLUCENT);
        imageEx (.createCompatibleImage (.getDeviceConfiguration graphics2D) width height Transparency/TRANSLUCENT)
        graphics2DEx (.createGraphics imageEx)
        content (if (nil? current-user) (:site_name (public-settings/public-settings)) (:first_name current-user))
        ]
    (prn "content is :" content)

    (.dispose graphics2D)
    ;设定画笔颜色
    (.setColor graphics2DEx (Color. 211 211 211))
    ;设置画笔字体
    (.setFont graphics2DEx font)
    ;设定倾斜度
    (.shear graphics2DEx 0.1, -0.26)
    ;设置字体平滑
    (.setRenderingHint graphics2DEx (RenderingHints/KEY_ANTIALIASING) (RenderingHints/VALUE_ANTIALIAS_ON))
    ;需要打印时间戳并且字符长度超过4
    (when (and needDateTime (> (.length content) 4))
      (prn "生成带时间戳的换行水印")
      (.drawString graphics2DEx content 0 (- height (.getSize font)))
      (.drawString graphics2DEx (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm") (new java.util.Date)) 0 height))
    ;需要打印时间戳并且字符长度不超过4
    (when (and needDateTime (<= (.length content) 4))
      (prn "生成带时间戳的水印")
      (.drawString graphics2DEx (str content (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm") (new java.util.Date))) 0 height))
    ;不需要打印时间戳
    (when (not needDateTime)
      (prn "生成不带时间戳的水印")
      (.drawString graphics2DEx content 0 (- height (.getSize font))))

    ;释放画笔
    (.dispose graphics2DEx)

    imageEx))

(defn add-background-image-to-sheet
  "add image to sheet as background"
  [sheet-name workbook]
  (try
    (let [sheet (.getSheet workbook sheet-name)
          current-user @api/*current-user*
          ^BufferedImage bufferedImage (generate-watermark-image current-user true)
          ^ByteArrayOutputStream os (ByteArrayOutputStream.)]

      (prn "*current-user* is :" (:first_name current-user))
      (ImageIO/write bufferedImage "png" os)
      (let [bytes (.toByteArray os)

            pictureIdx (.addPicture workbook bytes Workbook/PICTURE_TYPE_PNG)
            ;String rID = sheet.addRelation(null, XSSFRelation.IMAGES, workbook.getAllPictures().get(pictureIdx)).getRelationship().getId();
            rID (.getId (.getRelationship (.addRelation sheet, nil, XSSFRelation/IMAGES (.get (.getAllPictures workbook) pictureIdx))))
            ]
        ;sheet.getCTWorksheet().addNewPicture().setId(rID);
        (.setId (.addNewPicture (.getCTWorksheet sheet)) rID))
      ;(with-open [stream (FileInputStream. "/mnt/xvdb/springboot/metabase/data_image.png")]
      ;  (let [bytes (IOUtils/toByteArray stream)
      ;        pictureIdx (.addPicture workbook bytes Workbook/PICTURE_TYPE_PNG)
      ;        ;String rID = sheet.addRelation(null, XSSFRelation.IMAGES, workbook.getAllPictures().get(pictureIdx)).getRelationship().getId();
      ;        rID (.getId (.getRelationship (.addRelation sheet, nil, XSSFRelation/IMAGES  (.get (.getAllPictures workbook) pictureIdx))))
      ;        ]
      ;    ;sheet.getCTWorksheet().addNewPicture().setId(rID);
      ;    (.setId (.addNewPicture (.getCTWorksheet sheet)) rID)))
      workbook)
    (catch Exception e
      (log/error e "Error writing image to output stream")))
  )

(defn- results->cells
  "Convert the resultset to a seq of rows with the first row as a header"
  [results]
  (cons (map :display_name (get-in results [:result :data :cols]))
        (get-in results [:result :data :rows])))

(defn- export-to-xlsx [column-names rows]
  (let [wb  (spreadsheet/create-workbook "Query result" (cons (mapv name column-names) rows))
        ;; note: byte array streams don't need to be closed
        out (ByteArrayOutputStream.)]
    (add-background-image-to-sheet "Query result" wb)
    (spreadsheet/save-workbook! out wb)
    (ByteArrayInputStream. (.toByteArray out))))

(defn export-to-xlsx-file
  "Write an XLS file to `file` with the header a and rows found in `results`"
  [^File file, results]
  (let [file-path (.getAbsolutePath file)]
    (->> (results->cells results)
         (spreadsheet/create-workbook "Query result")
         (add-background-image-to-sheet "Query result")
         (spreadsheet/save-workbook! file-path))))

(defn- export-to-csv [column-names rows]
  (with-out-str
    ;; turn keywords into strings, otherwise we get colons in our output
    (csv/write-csv *out* (into [(mapv name column-names)] rows))))

(defn export-to-csv-writer
  "Write a CSV to `file` with the header a and rows found in `results`"
  [^File file results]
  (with-open [fw (java.io.FileWriter. file)]
    (csv/write-csv fw (results->cells results))))

(defn- export-to-json [column-names rows]
  (for [row rows]
    (zipmap column-names row)))

;; TODO - we should rewrite this whole thing as 4 multimethods. Then it would be possible to add new export types via
;; plugins, etc.
(def export-formats
  "Map of export types to their relevant metadata"
  {"csv"  {:export-fn    export-to-csv
           :content-type "text/csv"
           :ext          "csv"
           :context      :csv-download},
   "xlsx" {:export-fn    export-to-xlsx
           :content-type "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
           :ext          "xlsx"
           :context      :xlsx-download},
   "json" {:export-fn    export-to-json
           :content-type "applicaton/json"
           :ext          "json"
           :context      :json-download}})
