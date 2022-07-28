package org.jetbrains.plugins.scala.conversion.ast

case class LocalVariable(modifiers: IntermediateNode, name: IntermediateNode, ftype: IntermediateNode,
                         isVar: Boolean, initalaizer: Option[IntermediateNode]) extends IntermediateNode with TypedElement{
    override def getType: TypeConstruction = ftype.asInstanceOf[TypedElement].getType
}
