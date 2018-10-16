package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScClassImpl

/**
 * @author ilyas
 */
class ScClassDefinitionElementType extends ScTemplateDefinitionElementType[ScClass]("class definition") {

  override def createElement(node: ASTNode) = new ScClassImpl(node)

  override def createPsi(stub: ScTemplateDefinitionStub) = new ScClassImpl(stub)
}
