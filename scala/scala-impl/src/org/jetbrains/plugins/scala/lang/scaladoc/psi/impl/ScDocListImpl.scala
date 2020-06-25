package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocList, ScDocListItem}

class ScDocListImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocList {
  override def toString: String = "ScDocList"

  override def items: Seq[ScDocListItem] =
    findChildrenByClass(classOf[ScDocListItem])
}