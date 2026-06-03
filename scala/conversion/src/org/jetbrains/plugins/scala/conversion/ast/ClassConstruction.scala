package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.ast.ClassConstruction.ClassType.ClassType

abstract class ClassLikeConstruction extends IntermediateNode

object ClassConstruction {
  object ClassType extends Enumeration {
    type ClassType = Value
    val CLASS, OBJECT, INTERFACE, ENUM, ANONYMOUS = Value
  }
}

case class ClassConstruction(
  name: NameIdentifier,
  primaryConstructor: Option[PrimaryConstructor],
  bodyElements: Seq[IntermediateNode],
  modifiers: ModifiersConstruction,
  typeParams: Option[Seq[TypeParameterConstruction]],
  initializers: Option[Seq[IntermediateNode]],
  classType: ClassType,
  companion: IntermediateNode,
  extendsList: Option[Seq[IntermediateNode]]
) extends ClassLikeConstruction

case class AnonymousClass(
  mType: IntermediateNode,
  args: IntermediateNode,
  body: Seq[IntermediateNode],
  extendsList: Seq[IntermediateNode]
) extends ClassLikeConstruction with TypedElement {
  override def getType: TypeConstruction = mType.asInstanceOf[TypedElement].getType
}

case class Enum(
  name: NameIdentifier,
  modifiers: ModifiersConstruction,
  enumConstants: Seq[IntermediateNode]
) extends ClassLikeConstruction