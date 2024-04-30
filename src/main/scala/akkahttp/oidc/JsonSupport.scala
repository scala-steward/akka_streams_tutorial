package akkahttp.oidc

import io.circe.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

trait JsonSupport {

  case class Keys(keys: Seq[KeyData])
  case class KeyData(kid: String, n: String, e: String)

  implicit val keyDataEncoder: Encoder[KeyData] = deriveEncoder[KeyData]
  implicit val keyDataDecoder: Decoder[KeyData] = deriveDecoder[KeyData]

  implicit val keysEncoder: Encoder[Keys] = deriveEncoder[Keys]
  implicit val keysDecoder: Decoder[Keys] = deriveDecoder[Keys]

  case class UserKeycloak(firstName: Option[String], lastName: Option[String], email: Option[String])

  implicit val userEncoder: Encoder[UserKeycloak] = deriveEncoder[UserKeycloak]
  implicit val userDecoder: Decoder[UserKeycloak] = deriveDecoder[UserKeycloak]

  case class UsersKeycloak(users: Seq[UserKeycloak])

  implicit val usersEncoder: Encoder[UsersKeycloak] = deriveEncoder[UsersKeycloak]
  implicit val usersDecoder: Decoder[UsersKeycloak] = deriveDecoder[UsersKeycloak]

}
