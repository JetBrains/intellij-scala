package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTraitImpl

/**
 * @author ilyas
 */
class ScTraitDefinitionElementType extends ScTemplateDefinitionElementType[ScTrait]("trait definition") {
  def createElement(node: ASTNode): ScTrait = new ScTraitImpl(node)

  def createPsi(stub: ScTemplateDefinitionStub): ScTrait = new ScTraitImpl(stub)
}
