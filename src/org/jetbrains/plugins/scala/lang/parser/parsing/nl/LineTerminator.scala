package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package nl

/** 
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
*/

object LineTerminator {
  def isSingle(s: String): Boolean = s.indexOf('\n', 1) == -1
  def apply(s: String): Boolean = isSingle(s)
}