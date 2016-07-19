package org.jetbrains.plugins.dotty.lang.psi.stubs.elements

import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTraitDefinitionElementType

/**
  * @author adkozlov
  */
class DottyTraitDefinitionElementType
  extends ScTraitDefinitionElementType with DottyDefaultStubSerializer[ScTemplateDefinitionStub]
