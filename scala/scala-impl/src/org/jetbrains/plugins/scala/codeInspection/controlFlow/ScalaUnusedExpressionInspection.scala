package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiWhiteSpace}
import org.jetbrains.plugins.scala.codeInspection.controlFlow.ScalaUnusedExpressionInspection.{HasSideEffects, NoSideEffects, OnlyThrows, RangeCollector, SideEffectKind, createQuickFixes}
import org.jetbrains.plugins.scala.codeInspection.quickfix.RemoveExpressionQuickFix
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle, expressionResultIsNotUsed, findDefiningFunction, isUnitFunction}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker
import org.jetbrains.plugins.scala.util.SideEffectsUtil.{hasNoSideEffects, hasNoSideEffectsItself, mayOnlyThrow}

import scala.collection.mutable

final class ScalaUnusedExpressionInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
      case expression: ScExpression if IntentionAvailabilityChecker.checkInspection(this, expression.getParent) &&
        expressionResultIsNotUsed(expression) =>

        for {
          case (range, effect) <- collectRanges(expression)
          case descriptionTemplate <-
            effect match {
              case NoSideEffects => Some(ScalaInspectionBundle.message("unused.expression.no.side.effects"))
              case OnlyThrows => Some(ScalaInspectionBundle.message("unused.expression.throws"))
              case HasSideEffects => None
            }
        } {
          val quickfixes =
            if (range == expression.getTextRange) createQuickFixes(expression)
            else Array.empty[LocalQuickFix]
          holder.registerProblem(expression, range.shiftLeft(expression.startOffset), descriptionTemplate, quickfixes: _*)
        }
      case _ =>
    }

  private def collectRanges(expr: ScExpression): Seq[(TextRange, SideEffectKind)] = {
    val collector = new RangeCollector
    collectRanges(expr, collector)
    collector.result()
  }

  private def collectRanges(expr: ScExpression, collector: RangeCollector): Unit = {
    val range = expr.getTextRange
    SideEffectKind.from(expr) match {
      case HasSideEffects if hasNoSideEffectsItself(expr) =>
        val children = expr
          .depthFirst(psi => !psi.is[ScExpression, ScMember, PsiComment, PsiWhiteSpace] || (psi eq expr))
          .drop(1)
          .filterByType[ScExpression]
        var gapStart = range.getStartOffset
        if (children.hasNext) {
          children.foreach { child =>
            val childRange = child.getTextRange
            if (gapStart < childRange.getStartOffset) {
              collector += TextRange.create(gapStart, childRange.getStartOffset) -> NoSideEffects
            }
            collectRanges(child, collector)
            gapStart = childRange.getEndOffset
          }
          if (gapStart < range.getEndOffset) {
            collector += TextRange.create(gapStart, range.getEndOffset) -> NoSideEffects
          }
        } else {
          // strange case, where only the children have side effects, but we can't find any children
          collector += range -> HasSideEffects
        }
      case effect =>
        collector += range -> effect
    }
  }
}

object ScalaUnusedExpressionInspection {
  sealed abstract class SideEffectKind
  object SideEffectKind {
    def from(expr: ScExpression): SideEffectKind = {
      if (hasNoSideEffects(expr)) NoSideEffects
      else if (mayOnlyThrow(expr)) OnlyThrows
      else HasSideEffects
    }
  }
  case object NoSideEffects extends SideEffectKind
  case object OnlyThrows extends SideEffectKind
  case object HasSideEffects extends SideEffectKind

  private class RangeCollector {
    private val builder = mutable.Buffer.empty[(TextRange, SideEffectKind)]

    def +=(elem: (TextRange, SideEffectKind)): this.type = {
      val (range, effect) = elem
      builder.lastOption match {
        case Some((lastRange, lastEffect)) if lastRange.getEndOffset == range.getStartOffset && lastEffect == effect =>
          builder(builder.size - 1) = (new TextRange(lastRange.getStartOffset, range.getEndOffset), effect)
        case _ =>
          builder += elem
      }
      this
    }

    def result(): Seq[(TextRange, SideEffectKind)] = builder.toSeq
  }

  private def createQuickFixes(expression: ScExpression): Array[LocalQuickFix] = new RemoveExpressionQuickFix(expression) match {
    case quickFix if findDefiningFunction(expression).forall(isUnitFunction) => Array(quickFix)
    case quickFix => Array(quickFix, new AddReturnQuickFix(expression))
  }

  private[this] class AddReturnQuickFix(expression: ScExpression) extends AbstractFixOnPsiElement(
    ScalaInspectionBundle.message("add.return.keyword"),
    expression
  ) {
    override protected def doApplyFix(expression: ScExpression)
                                     (implicit project: Project): Unit = {
      val retStmt = ScalaPsiElementFactory.createExpressionWithContextFromText(s"return ${expression.getText}", expression.getContext, expression)
      expression.replaceExpression(retStmt, removeParenthesis = true)
    }
  }
}
