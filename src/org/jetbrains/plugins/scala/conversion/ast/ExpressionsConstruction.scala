package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.PrettyPrinter

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */

//TODO support multiline or something like this
case class ArrayAccess(expression: IntermediateNode, idxExpression: IntermediateNode)
  extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    expression.print(printer)
    printer.append("(")
    idxExpression.print(printer)
    printer.append(")")
  }
}

case class ClassCast(operand: IntermediateNode,
                     castType: IntermediateNode,
                     isPrimitive: Boolean) extends IntermediateNode with TypedElement {
  def canSimplify: Boolean = {
    isPrimitive && List("Int", "Long", "Double", "Float", "Byte", "Char", "Short").contains(castType.asInstanceOf[TypeConstruction].inType)
  }

  override def print(printer: PrettyPrinter): PrettyPrinter = {
    operand.print(printer)
    if (canSimplify) {
      printer.append(".to")
      castType.print(printer)
    } else {
      printer.append(".asInstanceOf[")
      castType.print(printer)
      printer.append("]")
    }
  }

  override def getType: TypeConstruction = castType.asInstanceOf[TypedElement].getType
}

case class ArrayInitializer(expresions: Seq[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append(expresions, ", ", "Array(", ")")
  }
}

case class BinaryExpressionConstruction(firstPart: IntermediateNode, secondPart: IntermediateNode,
                                        operation: String) extends IntermediateNode {

  override def print(printer: PrettyPrinter): PrettyPrinter = {
    firstPart.print(printer)
    printer.append(" ")
    printer.append(operation)
    printer.append(" ")
    secondPart.print(printer)
  }

}

case class ClassObjectAccess(expression: IntermediateNode) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("classOf[")
    expression.print(printer)
    printer.append("]")
  }

}

case class InstanceOfConstruction(operand: IntermediateNode,
                                  mtype: IntermediateNode) extends IntermediateNode with TypedElement {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    operand.print(printer)
    printer.append(".isInstanceOf[")
    mtype.print(printer)
    printer.append("]")
  }

  override def getType: TypeConstruction = mtype.asInstanceOf[TypedElement].getType
}

case class QualifiedExpression(qualifier: IntermediateNode, identifier: IntermediateNode) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    if (qualifier != null) {
      qualifier.print(printer)
      identifier.print(printer)
    }
    printer
  }
}

object MethodCallExpression extends IntermediateNode {
  def build(reciever: IntermediateNode, methodName: String, args: IntermediateNode): MethodCallExpression = {
    val identifier = methodName match {
      case "this" => LiteralExpression(methodName)
      case _ => LiteralExpression(escapeKeyword(methodName))
    }
    MethodCallExpression(methodName, if (reciever != null) QualifiedExpression(reciever, identifier) else identifier, args)
  }

  override def print(printer: PrettyPrinter): PrettyPrinter = printer.append("")
}

case class MethodCallExpression(name: String, method: IntermediateNode, args: IntermediateNode) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    method.print(printer)
    if (args != null)
      args.print(printer)
    printer
  }
}

case class ExpressionList(data: Seq[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append(data, ", ", "(", ")", data.nonEmpty)
  }
}

case class ThisExpression(value: Option[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("this")
    if (value.isDefined) value.get.print(printer)
    printer
  }
}

case class SuperExpression(value: Option[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("super")
    if (value.isDefined) value.get.print(printer)
    printer
  }
}

case class LiteralExpression(literal: String) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append(literal)
  }

  def getText = literal
}

case class ParenthesizedExpression(value: Option[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("(")
    if (value.isDefined) value.get.print(printer)
    printer.append(")")
  }
}

case class ReferenceExpression(value: IntermediateNode) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    value.print(printer)
  }

}

object NewExpression {
  def apply(anonymousClass: IntermediateNode): NewExpression = {
    NewExpression(null, null, null, anonymousClass)
  }

  def apply(mtype: IntermediateNode, arrayInitalizer: Seq[IntermediateNode],
            withArrayInitalizer: Boolean = true): NewExpression = {
    if (withArrayInitalizer)
      NewExpression(mtype, arrayInitalizer, null, null)
    else
      NewExpression(mtype, null, arrayInitalizer, null)
  }
}

case class NewExpression(mtype: IntermediateNode, arrayInitalizer: Seq[IntermediateNode],
                         arrayDimension: Seq[IntermediateNode],
                         anonClass: IntermediateNode) extends IntermediateNode with TypedElement {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    if (anonClass != null) {
      printer.append("new ")
      anonClass.print(printer)
    } else {
      if (arrayInitalizer != null) {
        mtype.print(printer)
        printer.append(arrayInitalizer, ", ", "(", ")", arrayInitalizer.nonEmpty)
      } else {
        printer.append("new ")
        mtype.print(printer)
        printer.append(arrayDimension, ", ", "(", ")",
          arrayDimension != null && arrayDimension.nonEmpty && !arrayDimension.head.isInstanceOf[ExpressionList])
      }
    }
  }

  override def getType: TypeConstruction = mtype.asInstanceOf[TypedElement].getType
}

case class PolyadicExpression(args: Seq[IntermediateNode], operation: String) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append(args, " " + operation + " ")
  }
}

case class PrefixExpression(operand: IntermediateNode, signType: String, canBeSimplified: Boolean) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    signType match {
      case "++" =>
        if (!canBeSimplified) {
          printer.append("({")
          operand.print(printer)
          printer.append(" += 1; ")
          operand.print(printer)
          printer.append("})")
        } else {
          operand.print(printer)
          printer.append(" += 1")
        }
      case "--" =>
        if (!canBeSimplified) {
          printer.append("({")
          operand.print(printer)
          printer.append(" -= 1; ")
          operand.print(printer)
          printer.append("})")
        } else {
          operand.print(printer)
          printer.append(" -= 1")
        }
      case _ =>
        printer.append(signType)
        operand.print(printer)
    }
  }
}

case class PostfixExpression(operand: IntermediateNode, signType: String, canBeSimplified: Boolean) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    signType match {
      case "++" =>
        if (!canBeSimplified) {
          printer.append("({")
          operand.print(printer)
          printer.append(" += 1; ")
          operand.print(printer)
          printer.append(" - 1")
          printer.append("})")
        } else {
          operand.print(printer)
          printer.append(" += 1")
        }
      case "--" =>
        if (!canBeSimplified) {
          printer.append("({")

          operand.print(printer)
          printer.append(" -= 1; ")
          operand.print(printer)
          printer.append(" + 1")

          printer.append("})")
        } else {
          operand.print(printer)
          printer.append(" -= 1")
        }
    }
  }
}