package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScClassImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

/**
 * @author ilyas
 */

class ScClassDefinitionElementType extends ScTemplateDefinitionElementType[ScClass]("class definition") {
  def createElement(node: ASTNode): PsiElement = new ScClassImpl(node)

  def createPsi(stub: ScTemplateDefinitionStub): ScClass = new ScClassImpl(stub)
}
