package org.jetbrains.plugins.scala.lang.parser

import org.jetbrains.annotations.PropertyKey
import org.jetbrains.plugins.scala.ScalaBundle

object ErrMsg{
  //noinspection DynamicPropertyKey
  def apply(@PropertyKey(resourceBundle = "messages.ScalaBundle") msg: String): String =
    ScalaBundle.message(msg)
}