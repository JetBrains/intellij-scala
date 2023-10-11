package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInsight.unwrap.{ScalaUnwrapContext, ScalaWhileUnwrapper}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractFixOnTwoPsiElements, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScDo
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction

import scala.annotation.tailrec
import scala.collection.mutable

final class ScalaUnreachableCodeInspection extends LocalInspectionTool {

  import ScalaUnreachableCodeInspection._

  private def problemDescriptors(
    element: PsiElement,
    @Nls descriptionTemplate: String,
  )(implicit manager: InspectionManager, isOnTheFly: Boolean): Seq[ProblemDescriptor] =
    element match {
      case definition: ScFunctionDefinition =>
        for {
          cfg <- Seq(definition.getControlFlow)
          unreachableInstructions = findUnreachableInstructions(cfg)
          if unreachableInstructions.nonEmpty

          fragment <- fragments(cfg, unreachableInstructions, definition)
          if fragment.nonEmpty

          head = fragment.head
          last = fragment.last
        } yield manager.createProblemDescriptor(
          head,
          last,
          descriptionTemplate,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          isOnTheFly,
          createQuickFix(head, last)
        )
      case _ => Nil
    }

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = new PsiElementVisitor {

    private val descriptionTemplate = getDisplayName

    override def visitElement(element: PsiElement): Unit = for {
      descriptor <- problemDescriptors(element, descriptionTemplate)(holder.getManager, isOnTheFly)
    } holder.registerProblem(descriptor)
  }
}

object ScalaUnreachableCodeInspection {
  private def findUnreachableInstructions(cfg: Seq[Instruction]): collection.Set[Instruction] = {
    // first, find all unreachable instructions
    val unreachable = mutable.Set.from(cfg)
    val queue = mutable.Queue.empty[Instruction]

    def markInstrFound(instr: Instruction): Unit =
      if (unreachable.remove(instr)) {
        queue.enqueue(instr)
      }

    markInstrFound(cfg.head)

    while (queue.nonEmpty) {
      val instr = queue.dequeue()
      instr.succ.foreach(markInstrFound)
    }

    unreachable
  }

  private def fragments(cfg: Seq[Instruction],
                        unreachable: collection.Set[Instruction],
                        scope: PsiElement): Seq[Seq[PsiElement]] = {
    // First get all reachable elements in the scope (definition of the cfg)
    val reachable = mutable.Set.empty[PsiElement]
    for {
      instr <- cfg
      if !unreachable(instr)
      elem <- instr.element
    } {
      elem
        .withParents
        .takeWhile(e => e != scope && reachable.add(e))
        .foreach(_ => ())
    }

    // Then group the upper most unreachable elements together when they have the same reachable parent
    val sortedInstrElements = cfg
      .flatMap(instr => instr.element)
      .sortBy(_.getTextOffset)

    @tailrec
    def upperMostUnreachableParent(element: PsiElement): (PsiElement, PsiElement) = {
      val parent = element.getParent
      if (parent != null && !reachable(parent))
        upperMostUnreachableParent(parent)
      else
        (element, parent)
    }

    val fragments = new FragmentsBuilder
    var curReachableParent = Option.empty[PsiElement]

    // go through all the elements, determine upper most unreachable and reachable parents
    // then group all upper unreachable elements together if they have the same reachable parent
    // and are not interrupted by a reachable element
    for (elem <- sortedInstrElements) {
      if (reachable(elem)) {
        fragments.commit()
      } else {
        val (unreachable, reachable) = upperMostUnreachableParent(elem)
        if (!curReachableParent.contains(reachable)) {
          fragments.commit()
          curReachableParent = Some(reachable)
        }
        fragments.add(unreachable)
      }
    }
    fragments.result()
  }

  private final class FragmentsBuilder {
    private type CurFragmentBuilder = mutable.Builder[PsiElement, Seq[PsiElement]]
    private val fragments = Seq.newBuilder[Seq[PsiElement]]
    private var currentFragment: Option[CurFragmentBuilder] = None

    def commit(): Unit = {
      currentFragment.foreach(fragments += _.result())
      currentFragment = None
    }

    def add(elem: PsiElement): Unit = {
      val cur = currentFragment match {
        case Some(cur) => cur
        case None =>
          val builder: CurFragmentBuilder = Seq.newBuilder
          currentFragment = Some(builder)
          builder
      }

      cur += elem
    }

    def result(): Seq[Seq[PsiElement]] = {
      commit()
      fragments.result()
    }
  }

  /**
    * Combines connected unreachable instructions into components
    */
  /*private def unreachableComponents(unreachable: collection.Set[Instruction]): Seq[Set[Instruction]] = {

    // now, collect them into components
    val alreadyAddedToComponent = mutable.Set.empty[Instruction]

    def searchComponent(instr: Instruction): Option[Set[Instruction]] = {
      if (alreadyAddedToComponent.contains(instr)) {
        None
      } else {
        val componentBuilder = Set.newBuilder[Instruction]
        val queue = mutable.Queue.empty[Instruction]

        def addToComponent(instr: Instruction): Unit = {
          if (unreachable(instr) && alreadyAddedToComponent.add(instr)) {
            componentBuilder += instr
            queue.enqueue(instr)
          }
        }

        addToComponent(instr)

        while (queue.nonEmpty) {
          val instr = queue.dequeue()
          instr.succ.foreach(addToComponent)
          instr.pred.foreach(addToComponent)
        }
        Some(componentBuilder.result())
      }
    }

    unreachable.iterator.flatMap(searchComponent).toSeq
  }*/

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
