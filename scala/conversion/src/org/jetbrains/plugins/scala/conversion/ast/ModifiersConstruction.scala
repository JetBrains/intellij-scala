package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.ast
import org.jetbrains.plugins.scala.conversion.ast.ModifierType.ModifierType

import scala.collection.mutable.ArrayBuffer

sealed abstract class Modifier extends IntermediateNode {
  def modificator: ModifierType
}

object ModifierType extends Enumeration {
  type ModifierType = Value

  val ABSTRACT, PUBLIC, PROTECTED, PRIVATE, PACKAGE_LOCAL, OVERRIDE, VOLATILE, TRANSIENT, NATIVE, THROW, SerialVersionUID, FINAL = Value

  val AccessModifiers: Seq[ast.ModifierType.Value] = Seq(
    ModifierType.PUBLIC,
    ModifierType.PRIVATE,
    ModifierType.PROTECTED,
    ModifierType.PACKAGE_LOCAL
  )

  override def toString(): String = {
    Value match {
      case ABSTRACT => "abstract"
      case _ => "other"
    }
  }
}

case class ModifiersConstruction(annotations: Seq[AnnotationConstruction], modifiers: Seq[Modifier]) extends IntermediateNode {
  val withoutList = new ArrayBuffer[ModifierType]()

  def without(value: ModifierType): this.type = {
    withoutList += value
    this
  }
}

case class ModifierWithExpression(mtype: ModifierType, value: IntermediateNode) extends Modifier {
  override def modificator: ModifierType = mtype
}

case class SimpleModifier(mtype: ModifierType) extends Modifier {
  override def modificator: ModifierType = mtype
}
