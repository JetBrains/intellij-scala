package org.jetbrains.plugins.scala.conversion.visitors

import org.jetbrains.plugins.scala.conversion.ast.{IntermediateNode, LiteralExpression}

import scala.collection.mutable

final class PrintWithComments private() extends SimplePrintVisitor {

  private val printedComments = mutable.HashSet.empty[LiteralExpression]

  override protected def visit(node: IntermediateNode): Unit = {
    val IntermediateNode.Comments(beforeComments, afterComments, latestComments) = node.comments

    printComments(beforeComments)
    super.visit(node)
    printComments(afterComments ++ latestComments)
  }

  //override this function to be able to print last comments in block before "}"
  override protected def printBodyWithBraces(node: IntermediateNode)
                                            (printBodyFunction: => Unit): Unit =
    super.printBodyWithBraces(node) {
      printBodyFunction
      printComments(node.comments.latestComments)
    }

  private def printComments(comments: mutable.ArrayBuffer[LiteralExpression]): Unit =
    printWithSeparator(comments.filter(printedComments.add), "")
}

object PrintWithComments {

  def apply(node: IntermediateNode): PrintWithComments = {
    val visitor = new PrintWithComments()
    visitor.visit(node)
    visitor
  }
}