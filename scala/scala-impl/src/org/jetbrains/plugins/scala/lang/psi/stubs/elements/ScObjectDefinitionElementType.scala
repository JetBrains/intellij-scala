package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl

/**
 * @author ilyas
 */
class ScObjectDefinitionElementType extends ScTemplateDefinitionElementType[ScObject]("object definition") {

  override def createElement(node: ASTNode) = new ScObjectImpl(node)

  override def createPsi(stub: ScTemplateDefinitionStub) = new ScObjectImpl(stub)
}
