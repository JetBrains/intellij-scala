package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl

/**
 * @author ilyas
 */
class ScObjectDefinitionElementType extends ScTemplateDefinitionElementType[ScObject]("object definition") {
  def createElement(node: ASTNode): PsiElement = new ScObjectImpl(node)

  def createPsi(stub: ScTemplateDefinitionStub): ScObject = new ScObjectImpl(stub)
}
