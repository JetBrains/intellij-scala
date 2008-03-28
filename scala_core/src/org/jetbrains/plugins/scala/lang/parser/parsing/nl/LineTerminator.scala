package org.jetbrains.plugins.scala.lang.parser.parsing.nl

/** 
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
*/

object LineTerminator {
  def isSingle(s: String): Boolean = s.indexOf('\n', 1) == -1
  def apply(s: String): Boolean = isSingle(s)
}