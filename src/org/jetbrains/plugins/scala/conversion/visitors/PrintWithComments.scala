package org.jetbrains.plugins.scala.conversion.visitors

import org.jetbrains.plugins.scala.conversion.ast.{LiteralExpression, IntermediateNode}

import scala.collection.mutable

/**
  * Created by Kate Ustyuzhanina
  * on 11/24/15
  */
class PrintWithComments extends SimplePrintVisitor {
  override def visit(node: IntermediateNode): Unit = {
    printWithSeparator(node.comments.beforeComments, "")
    super.visit(node)
    printWithSeparator(node.comments.afterComments, "")
    val latest = node.comments.latestCommtets.filter(!printedComments.contains(_))
    printWithSeparator(latest, "")
    printedComments ++= latest ++ node.comments.afterComments ++ node.comments.beforeComments
  }

  override def printBodyWithCurlyBracketes(node: IntermediateNode, printBodyFunction: () => Unit): Unit = {
    printer.append(" { ")
    printBodyFunction()
    printWithSeparator(node.comments.latestCommtets, "")
    printedComments ++= node.comments.latestCommtets
    printer.append("}")
  }

  val printedComments = new mutable.HashSet[LiteralExpression]()
}
