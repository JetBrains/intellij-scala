package org.jetbrains.plugins.dotty.lang.psi.stubs.elements

import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScObjectDefinitionElementType

/**
  * @author adkozlov
  */
class DottyObjectDefinitionElementType extends ScObjectDefinitionElementType with DottyStubSerializer[ScTemplateDefinitionStub]
