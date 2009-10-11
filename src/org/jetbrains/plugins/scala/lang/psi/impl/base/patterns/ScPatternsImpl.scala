package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScPatternsImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatterns{
  override def toString: String = "ArgumentPatterns"

  def patterns: Seq[ScPattern] = findChildrenByClass(classOf[ScPattern]).toSeq
}