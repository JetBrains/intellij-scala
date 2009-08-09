package org.jetbrains.plugins.scala
package lang
package parser
package parsing

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.02.2009
 */

object ParserState {
  val EMPTY_STATE = 0
  val FILE_STATE = 1
  val SCRIPT_STATE = 2
  val ADDITIONAL_STATE = 3
}