package org.jetbrains.plugins.scala.lang.parser

import org.jetbrains.annotations.PropertyKey

object ErrMsg {
  //noinspection DynamicPropertyKey
  def apply(@PropertyKey(resourceBundle = "messages.ScalaParserBundle") msg: String): String =
    ScalaParserBundle.message(msg)
}
