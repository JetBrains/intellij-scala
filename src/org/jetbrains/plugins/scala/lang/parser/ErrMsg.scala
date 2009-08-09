package org.jetbrains.plugins.scala
package lang
package parser

/**
* @author ilyas
*/

object ErrMsg{
  def apply(msg: String) = {
    ScalaBundle.message(msg)
  }
}