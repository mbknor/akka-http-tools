package kjetland.akkaHttpTools.jwt.internal

case class OAuthTokenRequest
(
  grant_type:String,
  client_id:String,
  client_secret:String,
  audience:String
)
