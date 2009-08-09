package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDefinitionImpl
import api.statements.ScTypeAlias
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

class ScTypeAliasDefinitionElementType extends ScTypeAliasElementType[ScTypeAlias]("type alias definition"){
  def createElement(node: ASTNode): PsiElement = new ScTypeAliasDefinitionImpl(node)

  def createPsi(stub: ScTypeAliasStub) = new ScTypeAliasDefinitionImpl(stub)
}