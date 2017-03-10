package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.ast.ClassConstruction.ClassType.ClassType

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */
object ClassConstruction {

  object ClassType extends Enumeration {
    type ClassType = Value
    val CLASS, OBJECT, INTERFACE, ENUM, ANONYMOUS = Value
  }

}

case class ClassConstruction(name: IntermediateNode, primaryConstructor: Option[IntermediateNode], bodyElements: Seq[IntermediateNode],
                             modifiers: IntermediateNode, typeParams: Option[Seq[IntermediateNode]],
                             initalizers: Option[Seq[IntermediateNode]], classType: ClassType, companion: IntermediateNode,
                             extendsList: Option[Seq[IntermediateNode]]) extends IntermediateNode

case class AnonymousClass(mType: IntermediateNode, args: IntermediateNode, body: Seq[IntermediateNode],
                          extendsList: Seq[IntermediateNode]) extends IntermediateNode with TypedElement {
  override def getType: TypeConstruction = mType.asInstanceOf[TypedElement].getType
}

case class Enum(name: IntermediateNode, modifiers: IntermediateNode, enumConstants: Seq[IntermediateNode]) extends IntermediateNode