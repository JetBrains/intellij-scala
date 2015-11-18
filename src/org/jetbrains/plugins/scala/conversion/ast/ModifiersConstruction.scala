package org.jetbrains.plugins.scala.conversion.ast


import org.jetbrains.plugins.scala.conversion.PrettyPrinter
import org.jetbrains.plugins.scala.conversion.ast.ModifierType.ModifierType

import scala.collection.mutable.ArrayBuffer


/**
  * Created by user
  * on 10/27/15
  */
trait Modifier {
  def getModificator(): ModifierType
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
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    for (a <- annotations) {
      a.print(printer)
      printer.space()
    }

    //to prevent situation where access modifiers print earlier then throw
    val sortModifiers = modifiers.collect { case m: Modifier if !accessModifiers.contains(m.getModificator()) => m } ++
      modifiers.collect { case m: Modifier if accessModifiers.contains(m.getModificator()) => m }

    for (m <- sortModifiers) {
      if (!withoutList.contains(m.asInstanceOf[Modifier].getModificator())) {
        m.print(printer)
        printer.space()
      }
    }
    printer
  }

  private val withoutList = new ArrayBuffer[ModifierType]()

  def without(value: ModifierType): IntermediateNode = {
    withoutList += value
    this
  }

  def withoutAccessModifiers: IntermediateNode = {
    withoutList ++= accessModifiers
    this
  }

  def noModifiers = annotations.isEmpty && modifiers.isEmpty

  val accessModifiers = Seq(ModifierType.PUBLIC, ModifierType.PRIVATE, ModifierType.PROTECTED, ModifierType.PACKAGE_LOCAL)
}


case class ModifierWithExpression(mtype: ModifierType, value: IntermediateNode) extends IntermediateNode with Modifier {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    mtype match {
      case ModifierType.THROW =>
        printer.append("@throws[")
        value.print(printer)
        printer.append("]\n")
      case ModifierType.SerialVersionUID =>
        printer.append("@SerialVersionUID(")
        value.print(printer)
        printer.append(")\n")
      case ModifierType.PRIVATE =>
        printer.append("private[")
        value.print(printer)
        printer.append("] ")
      case _ =>
    }
    printer
  }

  override def getModificator(): ModifierType = mtype
}

case class SimpleModifier(mtype: ModifierType) extends IntermediateNode with Modifier {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append(mtype match {
      case ModifierType.ABSTRACT => "abstract"
      case ModifierType.PUBLIC => "public"
      case ModifierType.PROTECTED => "protected"
      case ModifierType.PRIVATE => "private"
      case ModifierType.OVERRIDE => "override"
      case ModifierType.FINAL => "final"
      case _ => ""
    })
    printer
  }

  override def getModificator(): ModifierType = mtype
}
