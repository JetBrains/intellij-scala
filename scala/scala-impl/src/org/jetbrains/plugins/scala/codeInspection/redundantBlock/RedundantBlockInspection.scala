package org.jetbrains.plugins.scala
package codeInspection.redundantBlock

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.codeInspection.parentheses.registerRedundantParensProblem
import org.jetbrains.plugins.scala.codeInspection.redundantBlock.RedundantBlockInspection.{InCaseClauseQuickFix, UnwrapExpressionQuickFix}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class RedundantBlockInspection extends AbstractInspection {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Unit] = {
    case (block: ScBlock) childOf ((blockOfExpr: ScBlock) childOf (_: ScCaseClause))
      if block.hasRBrace && block.getFirstChild.textMatches("{") &&
        blockOfExpr.getChildren.length == 1 && !block.getChildren.exists(_.is[ScCaseClauses]) =>
      registerRedundantParensProblem(ScalaInspectionBundle.message("redundant.braces.in.case.clause"), block, new InCaseClauseQuickFix(block))
    case block: ScBlockExpr if block.statements.length == 1 =>
      if (RedundantBlockInspection.isRedundantBlock(block)) {
        registerRedundantParensProblem(ScalaInspectionBundle.message("the.enclosing.block.is.redundant"), block, new UnwrapExpressionQuickFix(block))
      }
  }
}

object RedundantBlockInspection {
  private class UnwrapExpressionQuickFix(_block: ScBlockExpr) extends AbstractFixOnPsiElement[ScBlockExpr](ScalaInspectionBundle.message("unwrap.the.expression"), _block) {

    override protected def doApplyFix(block: ScBlockExpr)
                                     (implicit project: Project): Unit = {
      block.replace(block.lastStatement.get)
    }
  }

  private class InCaseClauseQuickFix(block: ScBlock) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("remove.redundant.braces"), block) {

    override protected def doApplyFix(bl: ScBlock)
                                     (implicit project: Project): Unit = {
      val children = bl.getChildren.drop(1).dropRight(1)
      for (child <- children) {
        bl.getParent.addBefore(child, bl)
      }
      bl.delete()
    }
  }

  def isRedundantBlock(block: ScBlock): Boolean = {
    val child: PsiElement = block.lastStatement.get
    val probablyRedundant = child match {
      case ref: ScReferenceExpression if ref.qualifier.isEmpty => true
      case t: ScThisReference if t.reference.isEmpty => true
      case _ => false
    }
    if (probablyRedundant) {
      val next: PsiElement = block.getNextSibling
      val parent = block.getParent
      val hasWhitespacesInBlock = block.getChildren.exists(_.is[PsiWhiteSpace])
      parent match {
        case _: ScArgumentExprList => false
        case _ if next == null && !hasWhitespacesInBlock => true
        case _: ScInterpolatedStringLiteral =>
          val text = child.getText
          val nextLetter = next.getText.headOption.getOrElse(' ')
          val checkId = isInterpolatedStringIdentifier(text) && (nextLetter == '$' || !isInterpolatedStringIdentifier(text + nextLetter))
          checkId && !text.startsWith("_") && !text.exists(_ == '$') && !text.startsWith("`")
        case _ => false
      }
    } else false
  }

  def isInterpolatedStringIdentifier(name: String): Boolean =
    name.length > 0 &&
      Character.isJavaIdentifierStart(name.head) &&
      name.forall(Character.isJavaIdentifierPart)
}
