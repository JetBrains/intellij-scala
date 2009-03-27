package org.jetbrains.plugins.scala.lang.parser

/**
* @author ilyas
*/

object ErrMsg{
  def apply(msg: String) = {
    ScalaBundle.message(msg)
  }
}