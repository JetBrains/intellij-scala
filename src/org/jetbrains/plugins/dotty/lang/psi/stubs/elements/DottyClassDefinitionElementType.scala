package org.jetbrains.plugins.dotty.lang.psi.stubs.elements

import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScClassDefinitionElementType

/**
  * @author adkozlov
  */
class DottyClassDefinitionElementType extends ScClassDefinitionElementType with DottyStubSerializer[ScTemplateDefinitionStub]
