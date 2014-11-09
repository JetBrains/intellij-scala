package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDeclarationImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

class ScTypeAliasDeclarationElementType extends ScTypeAliasElementType[ScTypeAlias]("type alias declaration"){
  def createElement(node: ASTNode): PsiElement = new ScTypeAliasDeclarationImpl(node)

  def createPsi(stub: ScTypeAliasStub) = new ScTypeAliasDeclarationImpl(stub)
}