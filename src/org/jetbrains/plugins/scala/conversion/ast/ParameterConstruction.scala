package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.PrettyPrinter

/**
 * Created by Kate Ustyuzhanina
 * on 10/27/15
 */
case class ParameterConstruction(modifiers: IntermediateNode, name: String,
                                 scCompType: IntermediateNode, isArray: Boolean) extends IntermediateNode with TypedElement{
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    modifiers.print(printer)
    printer.append(escapeKeyword(name))
    printer.append(": ")
    scCompType.print(printer)
    if (isArray) {
      printer.append("*")
    }
    printer
  }

  override def getType: TypeConstruction = scCompType.asInstanceOf[TypedElement].getType
}
