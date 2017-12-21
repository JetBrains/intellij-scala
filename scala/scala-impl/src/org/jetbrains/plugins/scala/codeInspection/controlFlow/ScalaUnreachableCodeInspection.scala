package org.jetbrains.plugins.scala
package codeInspection.controlFlow

import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.{LocalQuickFixOnPsiElement, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.unwrap.{ScalaUnwrapContext, ScalaWhileUnwrapper}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractFixOnTwoPsiElements, AbstractInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScDoStmt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.controlFlow.{ControlFlowUtil, Instruction}

import scala.annotation.tailrec
import scala.collection.{Set, SortedSet}

/**
  * Nikolay.Tropin
  * 2014-04-22
  */
class ScalaUnreachableCodeInspection extends AbstractInspection("ScalaUnreachableCode", "Unreachable code") {

  import ScalaUnreachableCodeInspection._

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case funDef: ScFunctionDefinition => ControlFlowUtil.detectConnectedComponents(funDef.getControlFlow()) match {
      case Seq(head, tail@_*) =>
        for {
          component <- tail
          unreachable = component.diff(head)
          if unreachable.nonEmpty

          fragment <- fragments(unreachable)
          fix <- createQuickFix(fragment)

          descriptor = new ProblemDescriptorImpl(fragment.head, fragment.last,
            "Unreachable code", Array(fix),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL, false, null, null, false)
        } {
          holder.registerProblem(descriptor)
        }
      case _ =>
    }
  }
}

object ScalaUnreachableCodeInspection {

  private def fragments(instructions: Set[Instruction]): Iterable[Set[PsiElement]] = {
    @tailrec
    def parentStatement(element: PsiElement): Option[PsiElement] = element.getParent match {
      case _: ScBlock |
           _: ScFunctionDefinition |
           ScDoStmt(_, `element`) => Some(element)
      case null => None
      case parent => parentStatement(parent)
    }

    implicit val ordering: Ordering[Instruction] = Ordering.by((_: Instruction).num)
    val elements = (SortedSet.empty[Instruction] ++ instructions)
      .flatMap(_.element)
      .map(e => parentStatement(e).getOrElse(e))

    elements.groupBy(_.getParent).values
  }

  private def createQuickFix(fragment: Set[PsiElement]): Option[LocalQuickFixOnPsiElement] =
    fragment.headOption.collect {
      case head childOf (doStatement@ScDoStmt(_, Some(condition))) if condition == head => new UnwrapDoStmtFix(doStatement)
      case head if fragment.size == 1 => new RemoveFragmentQuickFix(head)
      case head => new RemoveRangeQuickFix(head, fragment.last)
    }

  private[this] class RemoveRangeQuickFix(from: PsiElement, to: PsiElement)
    extends AbstractFixOnTwoPsiElements("Remove unreachable code", from, to) {

    override protected def doApplyFix(from: PsiElement, to: PsiElement)
                                     (implicit project: Project): Unit = {
      from.getParent.deleteChildRange(from, to)
    }
  }

  private[this] class RemoveFragmentQuickFix(fragment: PsiElement)
    extends AbstractFixOnPsiElement("Remove unreachable code", fragment) {

    override protected def doApplyFix(element: PsiElement)
                                     (implicit project: Project): Unit = {
      element.delete()
    }
  }

  private[this] class UnwrapDoStmtFix(doStatement: ScDoStmt)
    extends AbstractFixOnPsiElement("Unwrap do-statement", doStatement) {

    override protected def doApplyFix(doStatement: ScDoStmt)
                                     (implicit project: Project): Unit = {
      doStatement.getExprBody.foreach { _ =>
        val unwrapContext = new ScalaUnwrapContext
        unwrapContext.setIsEffective(true)
        new ScalaWhileUnwrapper().doUnwrap(doStatement, unwrapContext)
      }
    }
  }

}
