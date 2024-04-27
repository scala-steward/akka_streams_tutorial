package akkahttp.oidc

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol.*
import spray.json.RootJsonFormat

trait JsonSupport extends SprayJsonSupport {

  case class Keys(keys: Seq[KeyData])
  case class KeyData(kid: String, n: String, e: String)

  implicit val keyDataFormat: RootJsonFormat[KeyData] = jsonFormat3(KeyData)
  implicit val keysFormat: RootJsonFormat[Keys] = jsonFormat1(Keys)

  case class UserKeycloak(firstName: Option[String], lastName: Option[String], email: Option[String])
  case class UsersKeycloak(users: Seq[UserKeycloak])

  implicit val userJsonFormat: RootJsonFormat[UserKeycloak] = jsonFormat3(UserKeycloak)
  implicit val usersJsonFormat: RootJsonFormat[UsersKeycloak] = jsonFormat1(UsersKeycloak)
}