package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocListItem, ScPsiDocToken}

class ScDocListItemImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocListItem {
  override def toString: String = "ScDocListItem"

  override def headToken: ScPsiDocToken =
    findChildByType[ScPsiDocToken](ScalaDocTokenType.DOC_LIST_ITEM_HEAD)
}