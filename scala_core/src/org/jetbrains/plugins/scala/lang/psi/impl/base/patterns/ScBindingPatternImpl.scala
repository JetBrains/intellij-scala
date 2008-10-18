package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import api.statements.{ScFunctionDefinition, ScValue, ScVariable}
import api.toplevel.typedef.{ScTypeDefinition, ScMember}
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import lang.TokenSets
import lexer.ScalaTokenTypes
import api.base.patterns.ScBindingPattern
import psi.types._
import com.intellij.lang.ASTNode

abstract class ScBindingPatternImpl(node: ASTNode) extends ScPatternImpl(node) with ScBindingPattern {

  def nameId = findChildByType(TokenSets.ID_SET)

  def isWildcard = findChildByType(ScalaTokenTypes.tUNDER) != null

  override def getUseScope = {
    val func = PsiTreeUtil.getParentOfType(this, classOf[ScFunctionDefinition], true)
    if (func != null) new LocalSearchScope(func) else super.getUseScope
  }
}