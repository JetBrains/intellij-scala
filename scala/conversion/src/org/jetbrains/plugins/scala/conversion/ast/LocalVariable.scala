package org.jetbrains.plugins.scala.conversion.ast

case class LocalVariable(
  modifiers: ModifiersConstruction,
  name: NameIdentifier,
  ftype: TypeNode,
  isVar: Boolean,
  initializer: Option[IntermediateNode]
) extends ExpressionsHolderNodeBase(initializer.toSeq) with TypedElement {
  override def getType: TypeConstruction = ftype.asInstanceOf[TypedElement].getType
}
