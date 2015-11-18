package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.PrettyPrinter

/**
  * Created by Kate Ustyuzhanina
  * on 10/26/15
  */
case class MethodConstruction(modifiers: IntermediateNode, name: String, typeParams: Seq[IntermediateNode],
                              params: IntermediateNode, body: Option[IntermediateNode], retType: IntermediateNode) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    modifiers.print(printer)
    printer.append("def ")
    printer.append(escapeKeyword(name))

    if (typeParams.nonEmpty) {
      printer.append(typeParams, ", ", "[", "]")
    }


    params.print(printer)
    if (retType != null) {
      printer.append(": ")
      retType.print(printer)
    }

    if (body.isDefined) {
      if (retType != null) printer.append(" = ")
      body.get.print(printer)
    }
    printer
  }
}

trait Constructor

case class ConstructorSimply(modifiers: IntermediateNode, name: String, typeParams: Seq[IntermediateNode],
                             params: IntermediateNode, body: Option[IntermediateNode]) extends IntermediateNode {

  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("def ")
    printer.append("this")
    if (typeParams.nonEmpty) {
      printer.append(typeParams, ", ", "[", "]")
    }

    params.print(printer)

    val statements = body.asInstanceOf[Option[BlockConstruction]].get.statements

    def ifThisFirstStatement(): Boolean = {
      if (statements.isEmpty) false
      else
        statements.head match {
          case mc: MethodCallExpression if mc.name == "this" =>
            true
          case _ => false
        }
    }

    if (!ifThisFirstStatement() && body.isDefined) {
      body.get.asInstanceOf[BlockConstruction].addStatementBefore(LiteralExpression("this()"))
    }

    if (body.isDefined) body.get.print(printer)
    printer
  }
}

case class PrimaryConstruction(params: Seq[(String, IntermediateNode, Boolean)], superCall: IntermediateNode, body: Seq[IntermediateNode],
                               modifiers: IntermediateNode)
  extends IntermediateNode with Constructor {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    modifiers.print(printer)
    printer.space()
    if (params.nonEmpty) {
      printer.append("(")
      for ((param, ftype, isVar) <- params) {
        if (isVar)
          printer.append("var ")
        else
          printer.append("val ")
        printer.append(param)
        printer.append(": ")
        ftype.print(printer)
        printer.append(", ")

      }
      printer.delete(2)
      printer.append(")")
    }

    printer.space()

  }

  def printSuperCall(printer: PrettyPrinter): PrettyPrinter = {
    if (superCall != null)
      superCall.print(printer)
    printer
  }

  def printBody(printer: PrettyPrinter): PrettyPrinter = {
    if (body != null)
      printer.append(body, "\n", "", "\n")
    printer
  }
}



