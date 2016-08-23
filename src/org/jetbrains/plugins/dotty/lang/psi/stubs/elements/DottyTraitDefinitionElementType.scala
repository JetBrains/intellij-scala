package org.jetbrains.plugins.dotty.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.impl.toplevel.typedef.DottyTraitImpl
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTraitDefinitionElementType

/**
  * @author adkozlov
  */
class DottyTraitDefinitionElementType
  extends ScTraitDefinitionElementType with DottyDefaultStubSerializer[ScTemplateDefinitionStub] {
  override def createElement(node: ASTNode): ScTrait =
    new DottyTraitImpl(node)

  override def createPsi(stub: ScTemplateDefinitionStub): ScTrait =
    new DottyTraitImpl(stub)
}
