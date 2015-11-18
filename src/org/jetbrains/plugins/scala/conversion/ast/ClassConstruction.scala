package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.PrettyPrinter
import org.jetbrains.plugins.scala.conversion.ast.ClassConstruction.ClassType
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

case class ClassConstruction(name: String, primaryConstructor: Option[IntermediateNode], bodyElements: Seq[IntermediateNode],
                             modifiers: IntermediateNode, typeParams: Option[Seq[IntermediateNode]],
                             initalizers: Option[Seq[IntermediateNode]], classType: ClassType, companion: IntermediateNode,
                             extendsList: Option[Seq[IntermediateNode]]) extends IntermediateNode {

  override def print(printer: PrettyPrinter): PrettyPrinter = {
    if (companion.isInstanceOf[ClassConstruction]) {
      companion.print(printer)
      printer.newLine()
    }

    modifiers.print(printer)
    printer.append(classType match {
      case ClassType.CLASS => "class "
      case ClassType.OBJECT => "object "
      case ClassType.INTERFACE => "trait "
      case _ => ""
    })

    printer.append(escapeKeyword(name))
    if (typeParams.isDefined) printer.append(typeParams.get, ", ", "[", "]", typeParams.get.nonEmpty)

    if (primaryConstructor.isDefined) {
      printer.space()
      primaryConstructor.get.print(printer)
    }

    if (extendsList.isDefined && extendsList.get.nonEmpty) {
      printer.append(" extends ")

      extendsList.get.head.print(printer)
      if (primaryConstructor.isDefined) {
        primaryConstructor.get.asInstanceOf[PrimaryConstruction].printSuperCall(printer)
      }
      if (extendsList.get.tail.nonEmpty) printer.append(" with ")
      printer.append(extendsList.get.tail, " with ")
    }

    printer.append(" { ")
    if (primaryConstructor.isDefined) primaryConstructor.get.asInstanceOf[PrimaryConstruction].printBody(printer)
    printer.append(bodyElements, "\n", "", "")
    if (initalizers.isDefined) printer.append(initalizers.get, "\n", "\ntry ", "\n", initalizers.get.nonEmpty)
    printer.append("}")
  }
}


case class AnonymousClass(mType: IntermediateNode, args: IntermediateNode, body: Seq[IntermediateNode],
                          extendsList: Seq[IntermediateNode]) extends IntermediateNode with TypedElement {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    mType.print(printer)
    printer.append("(")
    args.print(printer)
    printer.append(")")

    if (extendsList != null && extendsList.nonEmpty) {
      printer.append(" with ")
      printer.append(extendsList, " with ")
    }
    printer.append(body, " ", "{ ", "}")
  }

  override def getType: TypeConstruction = mType.asInstanceOf[TypedElement].getType
}

case class Enum(name: String, modifiers: IntermediateNode, enumConstants: Seq[String]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    modifiers.print(printer)
    printer.append("object ")
    printer.append(escapeKeyword(name))
    printer.append(" extends Enumeration {\n")

    printer.append("type ")
    printer.append(escapeKeyword(name))
    printer.append(" = Value\n")

    if (enumConstants.nonEmpty) {
      printer.append("val ")
      for (str <- enumConstants) {
        printer.append(str)
        printer.append(", ")
      }
      printer.delete(2)
      printer.append(" = Value")
    }
    printer.append("\n}")
  }
}
