package org.jetbrains.plugins.scala.conversion.visitors

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.plugins.scala.conversion.ast.{IntermediateNode, LiteralExpression}

import scala.collection.mutable

final class PrintWithComments private() extends SimplePrintVisitor {

  //Different comments can same content.
  //We use `case class` for all intermediate nodes
  //So we need to use custom hash strategy to distinguish comments with same content
  private val printedComments: java.util.Set[LiteralExpression] = {
    val byEqStrategy: Hash.Strategy[LiteralExpression] = new Hash.Strategy[LiteralExpression] {
      override def hashCode(t: LiteralExpression): Int = t.hashCode()
      override def equals(t1: LiteralExpression, t2: LiteralExpression): Boolean = t1 eq t2
    }
    new ObjectOpenCustomHashSet[LiteralExpression](byEqStrategy)
  }


  override protected def visit(node: IntermediateNode): Unit = {
    val IntermediateNode.Comments(beforeComments, afterComments, latestComments) = node.comments

    printComments(beforeComments)
    super.visit(node)
    printComments(afterComments ++ latestComments)
  }

  //override this function to be able to print last comments in block before "}"
  override protected def printBodyWithBraces(node: IntermediateNode)
                                            (printBodyFunction: => Unit): Unit = {
    val IntermediateNode.Comments(beforeComments, afterComments, latestComments) = node.comments

    //Some comments can be duplicated in different nodes
    //If the commits belong to block node (for which we print the braces)
    //We "register" them as printed in order inner nodes don't print them in a wrong place
    val beforeCommentsNotPrinted = beforeComments.filter(printedComments.add)
    val afterCommentsNotPrinted = afterComments.filter(printedComments.add)

    printCommentsWithoutCheck(beforeCommentsNotPrinted)

    super.printBodyWithBraces(node) {
      printBodyFunction
      printComments(latestComments)
    }

    printCommentsWithoutCheck(afterCommentsNotPrinted)
  }

  private def printComments(comments: mutable.ArrayBuffer[LiteralExpression]): Unit = {
    val commentsFiltered = comments.filter(printedComments.add)
    printCommentsWithoutCheck(commentsFiltered)
  }

  private def printCommentsWithoutCheck(comments: mutable.ArrayBuffer[LiteralExpression]): Unit = {
    if (comments.nonEmpty) {
      printWithSeparator(comments, "")
    }
  }
}

object PrintWithComments {

  def visit(node: IntermediateNode): PrintWithComments = {
    val visitor = new PrintWithComments()
    visitor.visit(node)
    visitor
  }

  def print(node: IntermediateNode): String = {
    val printer = visit(node)
    printer.result()
  }
}