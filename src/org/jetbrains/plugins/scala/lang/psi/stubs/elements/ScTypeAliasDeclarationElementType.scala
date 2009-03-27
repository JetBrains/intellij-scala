package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDeclarationImpl
import api.statements.ScTypeAlias
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

class ScTypeAliasDeclarationElementType extends ScTypeAliasElementType[ScTypeAlias]("type alias declaration"){
  def createElement(node: ASTNode): PsiElement = new ScTypeAliasDeclarationImpl(node)

  def createPsi(stub: ScTypeAliasStub) = new ScTypeAliasDeclarationImpl(stub)
}