package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.codeInsight.unwrap.AbstractUnwrapper
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createNewLine

class ScalaUnwrapContext extends AbstractUnwrapper.AbstractContext {
  override def isWhiteSpace(element: PsiElement): Boolean = element.isInstanceOf[PsiWhiteSpace]

  def extractAllMembers(td: ScTemplateDefinition): Unit = {
    val members = td.members
    if (members.nonEmpty) extract(members.head, members.last, td)
  }

  def extractBlockOrSingleStatement(blockStmt: ScBlockStatement, from: PsiElement): Unit = blockStmt match {
    case block: ScBlock if block.statements.nonEmpty =>
      extract(block.statements.head, block.statements.last, from)
    case stmt: ScBlockStatement => extract(stmt, stmt, from)
    case _ =>
  }

  def insertNewLine(): Unit = {
    val lastExtracted = myElementsToExtract.get(myElementsToExtract.size() - 1)
    val newLine = createNewLine()(lastExtracted.getManager)
    if (myIsEffective && lastExtracted.isValid) {
      lastExtracted.getParent.addAfter(newLine, lastExtracted)
    }
  }

  def setElseBranch(ifStmt: ScIf, expr: ScExpression): Unit = {
    if (myIsEffective) {
      ifStmt.elseExpression match {
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
