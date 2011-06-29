package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeArgsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeArgs {
  override def toString: String = "TypeArgumentsList"
  
  def typeArgs: Seq[ScTypeElement] = findChildrenByClass(classOf[ScTypeElement]).toSeq
}