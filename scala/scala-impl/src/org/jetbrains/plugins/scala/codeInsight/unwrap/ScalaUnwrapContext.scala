package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.codeInsight.unwrap.AbstractUnwrapper
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createNewLine

/**
 * Nikolay.Tropin
 * 2014-06-26
 */
class ScalaUnwrapContext extends AbstractUnwrapper.AbstractContext {
  override def isWhiteSpace(element: PsiElement): Boolean = element.isInstanceOf[PsiWhiteSpace]

  def extractBlockOrSingleStatement(blockStmt: ScBlockStatement, from: PsiElement): Unit = blockStmt match {
    case block: ScBlock if block.statements.nonEmpty =>
      extract(block.statements.head, block.statements.last, from)
    case stmt: ScBlockStatement => extract(stmt, stmt, from)
    case _ =>
  }

  def insertNewLine() {
    val lastExtracted = myElementsToExtract.get(myElementsToExtract.size() - 1)
    val newLine = createNewLine()(lastExtracted.getManager)
    if (myIsEffective && lastExtracted.isValid) {
      lastExtracted.getParent.addAfter(newLine, lastExtracted)
    }
  }

  def setElseBranch(ifStmt: ScIf, expr: ScExpression) {
    if (myIsEffective) {
      ifStmt.elseBranch match {
        case Some(oldExpr) =>
          val replaced = oldExpr.replace(expr.copy())
          addElementToExtract(replaced)
        case _ =>
      }
    }
  }

  def setIsEffective(value: Boolean): Unit = {
    myIsEffective = value
  }
}
