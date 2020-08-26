package org.jetbrains.plugins.scala
package codeInspection
package controlFlow

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInsight.unwrap.{ScalaUnwrapContext, ScalaWhileUnwrapper}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScDo}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction

import scala.annotation.tailrec
import scala.collection.mutable

final class ScalaUnreachableCodeInspection extends LocalInspectionTool {

  import ScalaUnreachableCodeInspection._

  protected def problemDescriptors(element: PsiElement,
                                   @Nls descriptionTemplate: String,
                                   highlightType: ProblemHighlightType)
                                  (implicit manager: InspectionManager, isOnTheFly: Boolean): List[ProblemDescriptor] =
    element match {
      case definition: ScFunctionDefinition =>
        for {
          component <- unreachableComponents(definition.getControlFlow)
          if component.nonEmpty

          sortedComponent = collection.SortedSet.empty[PsiElement] ++ component.flatMap(_.element)
          fragment <- fragments(sortedComponent)
          if fragment.nonEmpty

          head = fragment.head
          last = fragment.last
        } yield manager.createProblemDescriptor(
          head,
          last,
          descriptionTemplate,
          highlightType,
          isOnTheFly,
          createQuickFix(head, last)
        )
      case _ => Nil
    }

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = new PsiElementVisitor {

    private val descriptionTemplate = getDisplayName

    override def visitElement(element: PsiElement): Unit = for {
      descriptor <- problemDescriptors(element, descriptionTemplate, ProblemHighlightType.LIKE_UNUSED_SYMBOL)(holder.getManager, isOnTheFly)
    } holder.registerProblem(descriptor)
  }
}

object ScalaUnreachableCodeInspection {

  private implicit val Ordering: Ordering[PsiElement] =
    (x: PsiElement, y: PsiElement) => x.getTextRange.getStartOffset
      .compareTo(y.getTextRange.getStartOffset)

  private def fragments(elements: collection.SortedSet[PsiElement]) = {
    @tailrec
    def parentStatement(element: PsiElement): Option[PsiElement] = element.getParent match {
      case _: ScBlock |
           _: ScFunctionDefinition |
           ScDo(_, `element`) => Some(element)
      case null => None
      case parent => parentStatement(parent)
    }

    elements
      .map(e => parentStatement(e).getOrElse(e))
      .groupBy(_.getParent)
      .values
      .filter(_.nonEmpty)
  }

  /**
    * Detects connected components in a control-flow graph
    */
  def unreachableComponents(cfg: Iterable[Instruction]): List[collection.Set[Instruction]] = {
    val queue = mutable.ListBuffer.newBuilder
      .addAll(cfg)
      .result()
      .sortBy(_.num)

    val buffer = mutable.ListBuffer.empty[collection.Set[Instruction]]

    def inner(instruction: Instruction): Unit = {
      val currentSet = mutable.HashSet.empty[Instruction]

      @tailrec
      def inner_(instructions: Iterable[Instruction]): Unit = {
        if (instructions.isEmpty) {
          buffer += currentSet
          queue --= currentSet
        } else {
          val currentSucc = mutable.ArrayBuffer.empty[Instruction]

          for {
            n <- instructions
            if currentSet.add(n)
          } currentSucc ++= n.succ

          inner_(currentSucc)
        }
      }

      inner_(List(instruction))
    }

    while (queue.nonEmpty) {
      inner(queue.head)
    }

    buffer.toList match {
      case head :: tail => tail.map(_ -- head)
      case _ => Nil
    }
  }

  private def createQuickFix(head: PsiElement, last: PsiElement) = head.getParent match {
    case doStatement@ScDo(_, Some(`head`)) => new UnwrapDoStmtFix(doStatement)
    case _ if head eq last => new RemoveFragmentQuickFix(head)
    case _ => new RemoveRangeQuickFix(head, last)
  }

  private[this] class RemoveRangeQuickFix(from: PsiElement, to: PsiElement) extends AbstractFixOnTwoPsiElements(
    ScalaInspectionBundle.message("remove.unreachable.code"),
    from,
    to
  ) {
    override protected def doApplyFix(from: PsiElement, to: PsiElement)
                                     (implicit project: Project): Unit = {
      from.getParent.deleteChildRange(from, to)
    }
  }

  private[this] class RemoveFragmentQuickFix(fragment: PsiElement) extends AbstractFixOnPsiElement(
    ScalaInspectionBundle.message("remove.unreachable.code"),
    fragment
  ) {
    override protected def doApplyFix(element: PsiElement)
                                     (implicit project: Project): Unit = {
      element.delete()
    }
  }

  private[this] class UnwrapDoStmtFix(doStatement: ScDo) extends AbstractFixOnPsiElement(
    ScalaInspectionBundle.message("unwrap.do.statement"),
    doStatement
  ) {
    override protected def doApplyFix(doStatement: ScDo)
                                     (implicit project: Project): Unit =
      if (doStatement.body.isDefined) {
        val unwrapContext = new ScalaUnwrapContext
        unwrapContext.setIsEffective(true)
        new ScalaWhileUnwrapper().doUnwrap(doStatement, unwrapContext)
      }
  }

}
