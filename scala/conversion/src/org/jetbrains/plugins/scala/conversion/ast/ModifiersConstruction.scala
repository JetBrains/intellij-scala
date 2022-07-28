package org.jetbrains.plugins.scala.conversion.ast


import org.jetbrains.plugins.scala.conversion.ast.ModifierType.ModifierType

import scala.collection.mutable.ArrayBuffer

trait Modifier {
  def modificator: ModifierType
}

object ModifierType extends Enumeration {
  type ModifierType = Value
  val ABSTRACT, PUBLIC, PROTECTED, PRIVATE, PACKAGE_LOCAL, ANNOTATION, OVERRIDE,
  INNER, VOLATILE, TRANSIENT, NATIVE, THROW, SerialVersionUID, FINAL = Value


  val accessModifiers =
    Seq(ModifierType.PUBLIC, ModifierType.PRIVATE, ModifierType.PROTECTED, ModifierType.PACKAGE_LOCAL)

  override def toString(): String = {
    Value match {
      case ABSTRACT => "abstract"
      case _ => "other"
    }
  }
}

case class ModifiersConstruction(annotations: Seq[IntermediateNode], modifiers: Seq[IntermediateNode]) extends IntermediateNode {
  val withoutList = new ArrayBuffer[ModifierType]()

  def without(value: ModifierType): IntermediateNode = {
    withoutList += value
    this
  }

  def withoutAccessModifiers: IntermediateNode = {
    withoutList ++= accessModifiers
    this
  }

  def noModifiers: Boolean = annotations.isEmpty && modifiers.isEmpty

  val accessModifiers = Seq(ModifierType.PUBLIC, ModifierType.PRIVATE, ModifierType.PROTECTED, ModifierType.PACKAGE_LOCAL)
}


case class ModifierWithExpression(mtype: ModifierType, value: IntermediateNode) extends IntermediateNode with Modifier {
  override def modificator: ModifierType = mtype
}

case class SimpleModifier(mtype: ModifierType) extends IntermediateNode with Modifier {
    override def modificator: ModifierType = mtype
}
