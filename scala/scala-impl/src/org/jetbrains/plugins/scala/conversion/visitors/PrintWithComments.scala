package org.jetbrains.plugins.scala
package conversion
package visitors

import scala.collection.mutable

/**
  * Created by Kate Ustyuzhanina
  * on 11/24/15
  */
final class PrintWithComments extends SimplePrintVisitor {

  import ast._

  private val printedComments = mutable.HashSet.empty[LiteralExpression]

  override def visit(node: IntermediateNode): Unit = {
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

  private def printComments(comments: mutable.ArrayBuffer[LiteralExpression]): Unit = {
    val unprinted = comments.filterNot(printedComments.contains)
    printWithSeparator(unprinted, "")
    printedComments ++= unprinted
  }
}
