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
    printComments(node.comments.beforeComments)
    super.visit(node)
    printComments(node.comments.afterComments ++ node.comments.latestCommtets)
  }

  //override this function to be able to print last comments in block before "}"
  override protected def printBodyWithBraces(node: IntermediateNode)
                                            (printBodyFunction: => Unit): Unit = {
    printer.append(" { ")
    printBodyFunction
    printComments(node.comments.latestCommtets)
    printer.append("}")
  }

  private def printComments(comments: mutable.ArrayBuffer[LiteralExpression]): Unit = {
    val unprinted = comments.filterNot(printedComments.contains)
    printWithSeparator(unprinted, "")
    printedComments ++= unprinted
  }
}
