package org.jetbrains.plugins.scala.conversion.visitors

import org.jetbrains.plugins.scala.conversion.ast.{IntermediateNode, LiteralExpression}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 11/24/15
  */
class PrintWithComments extends SimplePrintVisitor {
  override def visit(node: IntermediateNode): Unit = {
    printComments(node.comments.beforeComments)
    super.visit(node)
    printComments(node.comments.afterComments ++ node.comments.latestCommtets)
  }

  //override this function to be able to print last comments in block before "}"
  override def printBodyWithCurlyBracketes(node: IntermediateNode, printBodyFunction: () => Unit): Unit = {
    printer.append(" { ")
    printBodyFunction()
    printComments(node.comments.latestCommtets)
    printer.append("}")
  }

  def printComments(comments: ArrayBuffer[LiteralExpression]): Unit ={
    val unprinted = comments.filterNot(printedComments.contains)
    printWithSeparator(unprinted, "")
    printedComments ++= unprinted
  }

  val printedComments = new mutable.HashSet[LiteralExpression]()
}
