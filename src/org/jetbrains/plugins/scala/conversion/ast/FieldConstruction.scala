package org.jetbrains.plugins.scala.conversion.ast

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */
//TODO setter&getter
case class FieldConstruction(modifiers: IntermediateNode, name: String,
                             ftype: IntermediateNode, isVar: Boolean,
                             initalaizer: Option[IntermediateNode]) extends IntermediateNode with TypedElement {
  override def getType: TypeConstruction = ftype.asInstanceOf[TypedElement].getType
}
