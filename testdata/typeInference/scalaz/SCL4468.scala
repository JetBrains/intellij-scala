package foo

import scalaz.{Order => _, _}
import Scalaz._ //"Scalaz" is highlighted with error "Cannot resolve symbol Scalaz"

object Test {
  val something = /*start*/5.some/*end*/ //Implicit conversion to OptionW recognized by plugin here so import was recognized by plugin
}
//Option[Int]