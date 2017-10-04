package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScPatterns extends ScalaPsiElement {
  def patterns: Seq[ScPattern]
}