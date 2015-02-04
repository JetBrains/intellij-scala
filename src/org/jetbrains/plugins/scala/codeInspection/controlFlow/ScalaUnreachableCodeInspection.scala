package org.jetbrains.plugins.scala
package codeInspection.controlFlow

import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScDoStmt, ScBlock}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.controlFlow.{ControlFlowUtil, Instruction}

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 2014-04-22
 */
class ScalaUnreachableCodeInspection extends AbstractInspection("ScalaUnreachableCode", "Unreachable code"){
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case funDef: ScFunctionDefinition =>
      val cfg = funDef.getControlFlow(cached = false)
      val components = ControlFlowUtil.detectConnectedComponents(cfg)
      if (components.length > 1) {
        for {
          comp <- components.tail
          unreachable = comp.diff(components.head)
          fragm = fragment(unreachable)
        } {
          registerProblem(fragm, holder)
        }
      }
  }

  def getElementsRange(start: PsiElement, end: PsiElement): Seq[PsiElement] = {
    val commonParent = PsiTreeUtil.findCommonParent(start, end)
    if (start == commonParent || end == commonParent) return Seq(commonParent)

    val startRange = start.getTextRange
    val endRange = end.getTextRange

    val children = commonParent.children.toSeq
    val firstIdx = children.indexWhere(_.getTextRange.contains(startRange))
    val lastIdx = children.indexWhere(_.getTextRange.contains(endRange))
    children.take(lastIdx + 1).drop(firstIdx)
  }

  private def fragment(instructions: Iterable[Instruction]): Seq[PsiElement] = {
    if (instructions.size == 0) return Seq.empty
    if (instructions.size == 1) return instructions.head.element.toSeq

    @tailrec
    def getParentStmt(element: PsiElement): Option[PsiElement] = {
      element.getParent match {
        case _: ScBlock => Some(element)
        case _: ScFunctionDefinition => Some(element)
        case doStmt: ScDoStmt if doStmt.condition.contains(element) => Some(element)
        case null => None
        case parent => getParentStmt(parent)
      }
    }

    val elements = instructions.toSeq
            .sortBy(_.num)
            .flatMap(_.element)
            .map(e => getParentStmt(e).getOrElse(e))
            .distinct
    getElementsRange(elements.head, elements.last)
  }

  private def registerProblem(fragment: Seq[PsiElement], holder: ProblemsHolder) {
    if (fragment.isEmpty) return

    val descriptor = {
      val message = "Unreachable code"

      new ProblemDescriptorImpl(fragment.head, fragment.last, message, Array(new RemoveFragmentQuickFix(fragment)),
        ProblemHighlightType.LIKE_UNUSED_SYMBOL, false, null, null, false)
    }

    holder.registerProblem(descriptor)
  }
}

class RemoveFragmentQuickFix(fragment: Seq[PsiElement]) extends AbstractFixOnPsiElement("Remove unreachable code", fragment.head, fragment.last){
  override def doApplyFix(project: Project): Unit = {
    val parent = getStartElement.getParent
    parent.deleteChildRange(getStartElement, getEndElement)
  }
}