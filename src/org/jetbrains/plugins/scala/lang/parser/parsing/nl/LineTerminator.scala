package org.jetbrains.plugins.scala.lang.parser.parsing.nl

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 05.02.2008
* Time: 14:26:19
* To change this template use File | Settings | File Templates.
*/

object LineTerminator {
  def isSingle(s: String): Boolean = s.indexOf('\n', 1) == -1
  def apply(s: String): Boolean = isSingle(s)
}