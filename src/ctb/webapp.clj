(ns ctb.webapp
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :refer [trim]]
            [clojure.tools.logging :as log]
            [digest]
            [compojure.core :refer [defroutes GET POST ANY]]
            [compojure.route :refer [resources]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.basic-authentication :as basic]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.util.io :refer [piped-input-stream]]
            [org.lpetit.ring.servlet.util :as util]
            [ctb.manage-datasets :refer [map-user-datasets
                                         map-user-dataset-filename]]
            [ctb.views :refer [termlist-submission-form
                               display-termlist
                               expanded-termlist-review-page
                               term-cui-mapping-page
                               synset-table-page
                               synset-list-page
                               filtered-termlist-view
                               display-error-message
                               display-dataset-list
                               user-error-message]]
            [ctb.ring-utils :refer [TEMPDIR get-context-attribute]]
            [ctb.process-config :refer [handle-servlet-context
                                        handle-servlet-context-path]]
            [ctb.process :refer [get-appcontext
                                 mirror-termlist                                 
                                 process-termlist
                                 ;; process-termlist-and-termlistfile
                                 write-filtered-termlist
                                 process-filtered-synset
                                 termlist-to-cui-concept-map
                                 cui-concept-map-to-mrconso-write
                                 cui-concept-map-to-mrconso-recordlist
                                 expand-cui-concept-map
                                 termlist-to-cuiset
                                 cuicoll-to-custom-mrsty-write
                                 get-servlet-context-tempdir
                                 stream-mrconso]])
  (:import (javax.servlet ServletContext)))

(defn print-var
  [varname var]
  (.println System/out (str varname ": " (pr-str var))))

(defn authenticated? [name pass]
  (= [name pass] [(System/getenv "AUTH_USER") (System/getenv "AUTH_PASS")]))

;;  Drawbirdge handler for debugging with remote REPL (currently disabled)
;; 
;;      (def drawbridge-handler
;;        (-> (cemerick.drawbridge/ring-handler)
;;            ))
;;
;;      (defn wrap-drawbridge [handler]
;;        (fn [req]
;;          (let [handler (if (= "/repl" (:uri req))
;;                          (basic/wrap-basic-authentication
;;                           drawbridge-handler authenticated?)
;;                          handler)]
;;            (handler req))))

(defn wrap-user [handler]
  (fn [request]
    (if-let [user-id (-> request :cookies (get "termtool-user") :value)]
      (handler (assoc request :user user-id))
      (handler request))))

;; Determine if initialization has already occurred by checking
;; servlet context; if not then do any initialization and set state in
;; servlet context.
(defn wrap-context [handler]
  (fn [request]
    (get-appcontext request)
    (handler request)))

;; Determine if initialization has already occurred by checking
;; servlet context; if not then do any initialization and set state in
;; servlet context.
(defn wrap-context-path [handler]
  (fn [request]
    (handle-servlet-context-path (:servlet-context-path request))
    (handler request)))

  

;; # Current session and cookie information
;;
;; The cookie variable 'termtool-user' contains the current username
;;
;; The session variable 'user' also contains the current username,
;; taken from 'termtool-user' cookie.
;;
;; The session variable 'dataset' is currenty set using the sha-1
;; checksum of termlist supplied to the route POST /processtermlist/
;; by the 'Synset List HTML form.
;;
(defn set-session-username
  "Set session var dataset in response...

  WebBrowser state variables set by this function:

  cookies:
     termtool-user username for session
  sessioninfo:
     user  - same as termtool-user"
  ([cookies session]
   (if (:my-var session)
    {:body "Session variable already set"}
    {:body "Nothing in session, setting the var" 
     :session (assoc session :my-var "foo")}))
  ([cookies session body]
   (let [username (cond
                    (contains? cookies "termtool-user") (-> cookies (get "termtool-user") :value)
                    (contains? session :user) (:user session)
                    :else (str "user" (rand-int 100000)))]
     {:body body
      :cookies (assoc cookies :termtool-user username)
      :session (assoc session :user username)})))

  
;; # Web Applications (Routes)
;;
;; Primary URLs for Application
;;
;; * `/`                       `(GET)`  display initial form for input terms
;; * `/processtermlist/`       `(POST)` process input terms 
;; * `/filtertermlist/`        `(POST)` display expanded termlist form
;; * `/processfiltertermlist/` `(POST)` process expanded termlist using user's selections
(defroutes
  webroutes

  ;; (let [nrepl-handler (cemerick.drawbridge/ring-handler)]
  ;;   (ANY "/repl" request (nrepl-handler request)))

  (GET "/" {cookies :cookies session :session :as request}
    (get-appcontext request)
    (->
     (set-session-username cookies session (termlist-submission-form request "Custom Taxonomy Builder"))
     (assoc-in [:headers "Content-Type"] "text/html")))
  
  (POST "/processtermlist/" {cookies :cookies session :sessions params :params :as request}
    (let [{cmd "cmd" termlist "termlist" dataset "dataset"} params
          appcontext (get-appcontext request)]
       {:body
        (case cmd
          "submit"       (if (= (count (trim termlist)) 0)
                           (user-error-message request "User Input Error: Termlist is Empty" "User Input Error: Termlist is empty.")
                           (synset-list-page request (process-termlist appcontext dataset termlist)))
          "synset list"  (synset-list-page request (process-termlist appcontext dataset termlist))
          "test0"        (display-termlist request (mirror-termlist termlist))
          "test1"        (expanded-termlist-review-page request (process-termlist appcontext dataset termlist))
          "term->cui"    (term-cui-mapping-page request (process-termlist appcontext dataset termlist))
          "synset table" (synset-table-page request (process-termlist appcontext dataset termlist))
          (expanded-termlist-review-page request (process-termlist appcontext dataset termlist)) ; default
          )
        :session (assoc session :dataset (digest/sha-1 termlist)) ; add dataset key to session
        :cookies cookies
        :headers {"Content-Type" "text/html"}}))

  (POST "/filtertermlist/" req
    (get-appcontext req)
    (write-filtered-termlist req)
    (->
     (filtered-termlist-view req)
     (assoc-in [:headers "Content-Type"] "text/html")))

  (POST "/processfiltertermlist/" req
    (get-appcontext req)
    (->
     {:body (do
              (write-filtered-termlist req)
              (process-filtered-synset req)
              (filtered-termlist-view req))
      :session (:session req)
      :cookies (:cookies req)}
     (assoc-in [:headers "Content-Type"] "text/html")))

  (GET "/sessioninfo/" req
    (get-appcontext req)
      {:body 
       (str "request: <ul> <li>" (clojure.string/join "<li>" (mapv #(format "%s -> %s" (first %) (second %))
                                                                   req))
            "</ul>")
       :session (:session req)
       :cookies (:cookies req)
       :headers {"Content-Type" "text/html"}})
    

  (GET "/datasetsinfo/" {cookies :cookies session :session :as req}
    (get-appcontext req)
    {:body 
     (let [user (cond
                  (contains? session :user) (:user session)
                  (contains? cookies "termtool-user") (-> cookies (get "termtool-user") :value)
                  :else "NoUserName")]
       (if (= user "NoUserName")
         (display-error-message req "Error: no username in session or cookie!")
         (let [^ServletContext servlet-context (:servlet-context req)
               workdir (if servlet-context
                         (get-servlet-context-tempdir servlet-context)
                         "resources/public/output")]
           (log/info "workdir: " workdir)
           (display-dataset-list req user (map-user-datasets workdir user)))))
     :session session
     :cookies cookies
     :headers {"Content-Type" "text/html"}})


  (GET "/dataset/:dataset/:filename" {{dataset :dataset
                                       filename :filename} :params
                                      cookies :cookies
                                      session :session
                                      :as request}
    (get-appcontext request)
    {:body 
     (let [user (cond
                  (contains? session :user) (:user session)
                  (contains? cookies "termtool-user") (-> cookies (get "termtool-user") :value)
                  :else "NoUserName")]
       (if (= user "NoUserName")
         (display-error-message request "Error: no username in session or cookie!")
         (let [^ServletContext servlet-context (:servlet-context request)
               workdir (if servlet-context
                         (get-servlet-context-tempdir servlet-context)
                         "resources/public/output")
               filepath (map-user-dataset-filename workdir user dataset filename)]
           (if (.exists (io/file filepath))
             (slurp filepath)
             (str "File: " filename "(" filepath ") does not exist.")
             ))))
     :session session
     :cookies cookies
     :headers {"Content-Type" "text/html"}})
  
  ;; given a termlist in POST request skip filter step and go directly
  ;; to mrconso generation.
  ;; Note: we need to use piped-input-stream to avoid running out of memory.
  (POST "/rest/mrconso" {params :params :as request}
    (get-appcontext request)
    ;; (let [{termlist "termlist"} params
    ;;       cui-concept-map (expand-cui-concept-map (termlist-to-cui-concept-map termlist))
    ;;       stream-mrconso (fn [out]
    ;;                        (dorun
    ;;                         (map #(.write out %)
    ;;                              (cui-concept-map-to-mrconso-recordlist cui-concept-map))))]
    ;;   (piped-input-stream #(stream-mrconso (io/make-writer % {})))))
    (stream-mrconso params))
  
  ;; given a termlist in POST request skip filter step and go directly
  ;; to mrsty generation.
  (POST "/rest/mrsty"  {params :params :as request}
    (get-appcontext request)
    (let [{termlist "termlist"} params
          cuiset (termlist-to-cuiset termlist)
          stream-mrsty (fn [out]
                         (cuicoll-to-custom-mrsty-write out cuiset))]
      (piped-input-stream #(stream-mrsty (io/make-writer % {})))))

  (resources "/")

  )

(def app 
  (-> webroutes
      ;; wrap-drawbridge
      wrap-nested-params
      wrap-keyword-params
      wrap-params
      wrap-multipart-params
      wrap-session
      wrap-cookies
      wrap-user
      ))


