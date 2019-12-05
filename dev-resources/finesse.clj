(ns finesse
  (:require [clj-samplify.core :as samplify]
            [clj-spotify.util :as util]))

(def spotify-oauth2-params
  {:base-url "https://accounts.spotify.com"
   :auth-endpoint "/authorize"
   :token-endpoint "/api/token"
   :client-id (System/getenv "SPOTIFY_OAUTH2_CLIENT_ID")
   :redirect-uri "http://localhost:3000/oauth2"
   :scope "playlist-read-private playlist-modify-public playlist-modify-private ugc-image-upload user-follow-modify user-read-playback-state user-modify-playback-state user-read-currently-playing streaming"
   :custom-query-params {:show-dialog "true"}
   :client-secret (System/getenv "SPOTIFY_OAUTH2_CLIENT_SECRET")
   :provider :spotify})
