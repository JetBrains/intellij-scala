package org.jetbrains.plugins.scala.conversion.ast

case class ParameterConstruction(
  modifiers: IntermediateNode,
  name: IntermediateNode,
  scCompType: IntermediateNode,
  isVar: Option[Boolean],
  isArray: Boolean
) extends IntermediateNode with TypedElement {
  override def getType: TypeConstruction = scCompType.asInstanceOf[TypedElement].getType
}
