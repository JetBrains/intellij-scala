package org.jetbrains.plugins.dotty.lang.parser

import org.jetbrains.plugins.dotty.lang.psi.stubs.elements._
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ElementTypes

/**
  * @author adkozlov
  */
object DottyElementTypes extends ElementTypes {
  override val file = new DottyStubFileElementType

  override val classDefinition = new DottyClassDefinitionElementType
  override val objectDefinition = new DottyObjectDefinitionElementType
  override val traitDefinition = new DottyTraitDefinitionElementType

  val REFINED_TYPE = new ScalaElementType("refined type")
  val WITH_TYPE = new ScalaElementType("with type")
  val TYPE_ARGUMENT_NAME = new ScalaElementType("type argument name")
}
