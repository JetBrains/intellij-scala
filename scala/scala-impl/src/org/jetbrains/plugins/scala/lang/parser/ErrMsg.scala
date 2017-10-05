package org.jetbrains.plugins.scala
package lang
package parser

import org.jetbrains.annotations.PropertyKey

/**
* @author ilyas
*/

object ErrMsg{
  def apply(@PropertyKey(resourceBundle = "org.jetbrains.plugins.scala.ScalaBundle") msg: String): String = {
    ScalaBundle.message(msg)
  }
}