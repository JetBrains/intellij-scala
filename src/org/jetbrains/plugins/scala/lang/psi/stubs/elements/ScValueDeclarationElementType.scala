package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScValueDeclarationImpl
import api.statements.ScValue
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.10.2008
 */

class ScValueDeclarationElementType extends ScValueElementType[ScValue]("value declaration"){
  def createElement(node: ASTNode): PsiElement = new ScValueDeclarationImpl(node)

  def createPsi(stub: ScValueStub) = new ScValueDeclarationImpl(stub)
}