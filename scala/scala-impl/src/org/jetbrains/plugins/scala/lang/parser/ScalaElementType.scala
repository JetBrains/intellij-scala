package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

abstract class ScalaElementType(debugName: String,
                                override val isLeftBound: Boolean = true)
  extends IElementType(debugName, ScalaLanguage.INSTANCE)
    with SelfPsiCreator {

  def createElement(node: ASTNode): ScalaPsiElement

  override final def toString: String = super.toString
}
