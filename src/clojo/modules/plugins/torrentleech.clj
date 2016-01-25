(ns clojbot.modules.torrentleech
  (:require[clj-http.client       :as client]
           [clojure.tools.logging :as log   ]           
           [clojure.edn           :as edn   ]
           [clojbot.commands      :as cmd   ]
           [clojbot.botcore       :as core  ]
           [clojbot.utils         :as u     ]
           [clojure.string        :as str   ]
           [clojure.java.io       :as io    ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TORRENTLEECH URLS AND STUFF ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic header {
                       "User-Agent"  "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:24.0) Gecko/20100101 Firefox/24.0"
                       "Accept" "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                       "Accept-Encoding" "gzip, deflate"
                       "Accept-Language" "en-US,en;q=0.5"
                       "Connection" "keep-alive"})


(def ^:dynamic loginurl "http://torrentleech.org/user/account/login/")
(def ^:dynamic profileurl "http://torrentleech.org/profile/%s/#profileTab")
(def ^:dynamic homepage "http://torrentleech.org/torrents/browse/")


(def ^:dynamic replyformat "Ratio: %s Up: %s Down: %s")


(def ^:dynamic cookies nil)


;;;;;;;;;;;;;;;;;;;;;;
;; HELPER FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- get-page
  [url & args]
  (try (:body (apply client/get url args))
       (catch Exception e
         (log/error "GET" url "failed!\n" (.getMessage e))
         {:error "Error making request. Check logs."})))

(defn- post-page
  [url & args]
  (try (:body (apply client/post url args))
       (catch Exception e
         (log/error "POST" url "failed!\n" (.getMessage e))
         {:error "Error posting request. Check logs."})))


(defn- valid-profile?
  "Checks if the given page page constitues a valid profile."
  [page]
  (and (not (:error page))
       (re-find #".*Join\sDate.*" page)))


(defn- logged-in?
  "Requests the home page of torrentleech.org to see if the
  credentials are valid."
  []
  (let [page (get-page "http://torrentleech.org/torrents/browse/")]
    (if (:error page)
      page
      (.contains page "/user/account/logout"))))


(defn- do-login
  "Logs in. Assumes that the calls are wrapped in a binding for
  cookiejars (clj-http.core/*cookie-store*), as no explicit cookie
  passing is done."
  [username password]
  (post-page loginurl {:form-params  {"username" username "password" password}}))


(defn- get-userinfo
  "Scrapes the userinfo from the torrentleech profile page.
   Assumes a binding of cookiejar(clj-http.core/*cookie-store*)."
  [username]
  (let [page (get-page (format profileurl (str/lower-case username)))]
    (if  (valid-profile? page)
      ;; Scrape the info. Will return nil if the regex failed.
      (let [ratio (nth  (re-find #"<b>Ratio:</b>\s(.*?)<" page) 1)
            up    (nth  (re-find #"Up:</b></span>\s(.*?)<" page) 1)
            down  (nth  (re-find #"Down:</b></span>\s(.*?)<" page) 1)]
        {:ratio ratio :up up :down down})
      {:error "Invalid userprofile!"})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(core/defmodule
  :echomodule
  0
  (core/defcommand
    "u"
    (fn [srv args msg]
      (let [credentials (u/read-config "torrentleech.edn")]
        ;; Use a cookiestore binding. Each subsequent request using client will
        ;; use the proper cookies.
        (binding  [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
          ;; Login and get a cookie.
          (do-login (:username credentials) (:password credentials))
          (if (logged-in?)
            ;; Scrape the userinfo. Might return an error!  If "~u <username>"
            ;; take the username, else take the sender his nick.
            (let [info (get-userinfo (if args args (u/sender-nick msg)))]
              (if (:error info)
                (cmd/send-message srv (:channel msg) (:error info))
                (cmd/send-message srv (:channel msg) (format replyformat (:ratio info) (:up info) (:down info)))))
            ;; Let the channel know that it failed.
            (cmd/send-message srv (:channel msg) "Euhm, I can't seem to log in :<")))))))
