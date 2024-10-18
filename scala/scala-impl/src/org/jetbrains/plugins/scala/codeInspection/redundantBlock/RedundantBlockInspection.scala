package org.jetbrains.plugins.scala.codeInspection.redundantBlock

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.codeInspection.parentheses.registerRedundantParensProblem
import org.jetbrains.plugins.scala.codeInspection.redundantBlock.RedundantBlockInspection.{InCaseClauseQuickFix, UnwrapExpressionQuickFix}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

final class RedundantBlockInspection extends LocalInspectionTool with DumbAware {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case (block: ScBlock) childOf ((blockOfExpr: ScBlock) childOf (_: ScCaseClause))
      if block.hasRBrace && block.getFirstChild.textMatches("{") &&
        blockOfExpr.getChildren.lengthIs == 1 && !block.getChildren.exists(_.is[ScCaseClauses]) =>
      registerRedundantParensProblem(ScalaInspectionBundle.message("redundant.braces.in.case.clause"), block, new InCaseClauseQuickFix(block), holder, isOnTheFly)
    case block: ScBlockExpr if block.statements.lengthIs == 1 =>
      if (RedundantBlockInspection.isRedundantBlock(block)) {
        registerRedundantParensProblem(ScalaInspectionBundle.message("the.enclosing.block.is.redundant"), block, new UnwrapExpressionQuickFix(block), holder, isOnTheFly)
      }
    case _ =>
  }
}

object RedundantBlockInspection {
  private final class UnwrapExpressionQuickFix(_block: ScBlockExpr)
    extends AbstractFixOnPsiElement[ScBlockExpr](ScalaInspectionBundle.message("unwrap.the.expression"), _block)
      with DumbAware {
    override protected def doApplyFix(block: ScBlockExpr)
                                     (implicit project: Project): Unit =
      block.lastStatement.foreach(block.replace)
  }

  private final class InCaseClauseQuickFix(block: ScBlock)
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("remove.redundant.braces"), block)
      with DumbAware {
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
        case _: ScInterpolatedStringLiteral if next != null =>
          val text = child.getText
          val nextLetter = next.getText.headOption.getOrElse(' ')
          val checkId = isInterpolatedStringIdentifier(text) && (nextLetter == '$' || !isInterpolatedStringIdentifier(text + nextLetter))
          checkId && !text.startsWith("_") && !text.exists(_ == '$') && !text.startsWith("`")
        case _ => false
      }
    } else false
  }

  private def isInterpolatedStringIdentifier(name: String): Boolean =
    name.nonEmpty &&
      Character.isJavaIdentifierStart(name.head) &&
      name.forall(Character.isJavaIdentifierPart)
}
