package org.jetbrains.plugins.scala.conversion.ast

case class ParameterConstruction(
  modifiers: ModifiersConstruction,
  name: NameIdentifier,
  scCompType: TypeNode,
  isVar: Option[Boolean],
  isArray: Boolean
) extends IntermediateNode with TypedElement {
  override def getType: TypeConstruction = scCompType.asInstanceOf[TypedElement].getType
}
