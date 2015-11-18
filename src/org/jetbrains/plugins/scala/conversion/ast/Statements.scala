package org.jetbrains.plugins.scala.conversion.ast

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.conversion.PrettyPrinter

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */
case class IfStatement(condition: Option[IntermediateNode],
                       thenBranch: Option[IntermediateNode],
                       elseBranch: Option[IntermediateNode]) extends IntermediateNode {

  override def print(printer: PrettyPrinter):PrettyPrinter = {
    printer.append("if")
    printer.space()

    printer.append("(")
    if (condition.isDefined) condition.get.print(printer)
    printer.append(")")
    printer.space()

    if (thenBranch.isDefined) thenBranch.get.print(printer)
    if (elseBranch.isDefined) {
      printer.newLine()
      printer.append("else")
      printer.space()
      elseBranch.get.print(printer)
    }
    printer
  }
}

case class ReturnStatement(value: IntermediateNode) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("return")
    printer.space()
    value.print(printer)
  }
}

case class ThrowStatement(value: IntermediateNode) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("throw")
    printer.space()
    value.print(printer)
  }
}

case class AssertStatement(condition: Option[IntermediateNode], description: Option[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("assert")
    printer.space()
    printer.append("(")
    if (condition.isDefined) condition.get.print(printer)
    if (description.isDefined) {
      printer.append(",")
      printer.space()
      description.get.print(printer)
    }
    printer.append(")")
  }
}

case class ImportStatement(importValue: IntermediateNode, onDemand: Boolean) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("import ")
    importValue.print(printer)
    if (onDemand) {
      printer.append("._")
    }
    printer
  }
}

case class ImportStatementList(imports: Array[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    for (iimport <- imports) {
      iimport.print(printer)
      printer.newLine()
    }
    printer
  }
}

case class PackageStatement(statement: IntermediateNode) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("package ")
    statement.print(printer)
  }
}

case class JavaCodeReferenceStatement(qualifier: Option[IntermediateNode], parametrList: Option[IntermediateNode], name: String) extends IntermediateNode {
  private var range = new TextRange(0, 0)
  override def print(printer: PrettyPrinter): PrettyPrinter = {

    if (qualifier.isDefined) {
      qualifier.get.print(printer)
      printer.append(".")
    }

    val begin = printer.length
    name match {
      case "this" => printer.append(name)
      case "super" => printer.append(name)
      case _ => printer.append(escapeKeyword(name))
    }
    range = new TextRange(begin, printer.length)
    if (parametrList.isDefined) parametrList.get.print(printer)
    printer
  }

  override def getRange = range
}

case class ForeachStatement(iterParamName: String, iteratedValue: Option[IntermediateNode],
                            body: Option[IntermediateNode], isJavaCollection: Boolean) extends IntermediateNode {

  override def print(printer: PrettyPrinter): PrettyPrinter = {
    if (isJavaCollection) {
      printer.append("import scala.collection.JavaConversions._\n")
    }

    printer.append("for (")
    printer.append(escapeKeyword(iterParamName))
    printer.append(" <- ")
    if (iteratedValue.isDefined) iteratedValue.get.print(printer)
    printer.append(") ")
    if (body.isDefined) body.get.print(printer)
    printer
  }
}

case class ExpressionListStatement(exprs: Seq[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append(exprs, "\n")
  }
}

case class SynchronizedStatement(lock: Option[IntermediateNode], body: Option[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    if (lock.isDefined) lock.get.print(printer)
    printer.append(" synchronized ")
    if (body.isDefined) body.get.print(printer)
    printer
  }
}

case class SwitchLabelStatement(caseValue: Option[IntermediateNode], arrow: String) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("case ")
    if (caseValue.isDefined) caseValue.get.print(printer)
    printer.append(s" $arrow ")
  }
}

case class SwitchStatemtnt(expession: Option[IntermediateNode], body: Option[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    if (expession.isDefined) expession.get.print(printer)
    printer.append(" match ")
    if (body.isDefined) body.get.print(printer)
    printer
  }
}

case class TryCatchStatement(resourcesList: Seq[(String, IntermediateNode)], tryBlock: Option[IntermediateNode],
                             catchStatements: Seq[(IntermediateNode, IntermediateNode)],
                             finallyStatements: Option[Seq[IntermediateNode]], arrow: String) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    if (resourcesList != null && resourcesList.nonEmpty) {
      printer.append("try {\n")
      printer.append(resourcesList.map(_._2), "\n", "", "\n")
    }

    printer.append("try ")
    if (tryBlock.isDefined) tryBlock.get.print(printer)

    if (catchStatements.nonEmpty) {
      printer.append("\ncatch {\n")
      for ((parameter, block) <- catchStatements) {
        printer.append("case ")
        parameter.print(printer)
        printer.append(s" $arrow ")
        block.print(printer)
      }
      printer.append("}")
    }
    if (finallyStatements.isDefined) {
      if (resourcesList == null) {
        printer.append(" finally ")
        printer.append(finallyStatements.get, "\n")
      } else {
        printer.append(" finally {\n")
        printer.append(finallyStatements.get, "\n", "", "\n")
        resourcesList.foreach {
          case (name: String, variable: IntermediateNode) =>
            val cname = escapeKeyword(name)
            printer.append(s"if ($cname != null) $cname.close()\n")
        }

        printer.append("}")
      }
    } else if (resourcesList.nonEmpty) {
      printer.append(" finally {\n")
      resourcesList.foreach {
        case (name: String, variable: IntermediateNode) =>
          val cname = escapeKeyword(name)
          printer.append(s"if ($cname != null) $cname.close()\n")
      }
      printer.append("}")
    }
    if (resourcesList.nonEmpty) {
      printer.append("\n}")
    }
    printer
  }
}

object WhileStatement {
  val PRE_TEST_LOOP = 0
  val POST_TEST_LOOP = 1
}

case class WhileStatement(initialization: Option[IntermediateNode], condition: Option[IntermediateNode],
                          body: Option[IntermediateNode], update: Option[IntermediateNode], whileType: Int) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {

    def printDoWhile(): PrettyPrinter = {
      printer.append("do {\n")
      if (body.isDefined) body.get.print(printer)
      printer.append("\n}")
      if (update.isDefined) {
        printer.newLine()
        update.get.print(printer)
      }
      printer.append("while")
      printer.space()
      printer.append("(")
      if (condition.isDefined) condition.get.print(printer)
      printer.append(")")
    }

    def printWhile(): PrettyPrinter = {
      printer.append("while")
      printer.space()
      printer.append("(")
      if (condition.isDefined) condition.get.print(printer)
      printer.append(")")
      printer.space()
      printer.append("{\n")
      if (body.isDefined) body.get.print(printer)
      if (update.isDefined) {
        printer.newLine()
        update.get.print(printer)
      }
      printer.append("\n}")
    }

    if (initialization.isDefined) {
      initialization.get.print(printer)
      printer.newLine()
    }

    if (whileType == WhileStatement.PRE_TEST_LOOP) {
      printWhile()
    } else if (whileType == WhileStatement.POST_TEST_LOOP) {
      printDoWhile()
    }
    printer
  }
}

case class NotSupported(node: IntermediateNode, msg: String) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    if (node != null) node.print(printer)
    printer.space()
    printer.append(msg)
  }
}