package foo

import _root_.scalaz.{Order => _, _}
import _root_.scalaz.Scalaz._ //"Scalaz" is highlighted with error "Cannot resolve symbol Scalaz"

object Test {
  val something = /*start*/5.some/*end*/ //Implicit conversion to OptionW recognized by plugin here so import was recognized by plugin
}
//Option[Int]