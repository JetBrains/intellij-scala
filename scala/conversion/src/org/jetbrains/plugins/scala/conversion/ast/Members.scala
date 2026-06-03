package org.jetbrains.plugins.scala.conversion.ast

//TODO setter&getter
case class FieldConstruction(
  modifiers: ModifiersConstruction,
  name: NameIdentifier,
  ftype: TypeNode,
  isVar: Boolean,
  initializer: Option[IntermediateNode]
) extends IntermediateNode with TypedElement {
  override def getType: TypeConstruction = ftype.asInstanceOf[TypedElement].getType
}

case class MethodConstruction(
  modifiers: ModifiersConstruction,
  name: NameIdentifier,
  typeParams: Seq[TypeParameterConstruction],
  params: Seq[ParameterConstruction],
  body: Option[IntermediateNode],
  retType: Option[TypeNode]
) extends ExpressionsHolderNodeBase(body.toSeq)


trait Constructor

case class ConstructorSimply(
  modifiers: ModifiersConstruction,
  typeParams: Seq[TypeParameterConstruction],
  params: Seq[ParameterConstruction],
  body: Option[IntermediateNode]
) extends ExpressionsHolderNodeBase(body.toSeq)

case class PrimaryConstructor(
  params: Seq[ParameterConstruction],
  superCall: IntermediateNode,
  body: Option[BlockConstruction],
  modifiers: ModifiersConstruction
) extends ExpressionsHolderNodeBase(body.toSeq) with Constructor

case class EnumConstruction(name: NameIdentifier) extends IntermediateNode