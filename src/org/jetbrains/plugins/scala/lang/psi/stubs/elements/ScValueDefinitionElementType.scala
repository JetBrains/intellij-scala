package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScPatternDefinitionImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.10.2008
  */
class ScValueDefinitionElementType extends ScValueElementType[ScPatternDefinition]("value definition") {
  override def createElement(node: ASTNode): ScPatternDefinition = new ScPatternDefinitionImpl(node)

  override def createPsi(stub: ScValueStub): ScPatternDefinition = new ScPatternDefinitionImpl(stub)
}