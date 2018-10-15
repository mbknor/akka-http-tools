package kjetland.akkaHttpTools.jwt.internal

import java.time.OffsetDateTime

case class OAuthTokenResponse
(
  access_token:String,
  scope:String,
  expires_in:Long,
  token_type:String,
  expiresAt:OffsetDateTime
)
