package org.jetbrains.plugins.scala.codeInspection.implicits

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.implicits.OldStyleAggregateContextBoundsInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

final class OldStyleAggregateContextBoundsInspection extends LocalInspectionTool with DumbAware {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case boundsOwner: ScTypeBoundsOwner if boundsOwner.contextBounds.size > 1
      && boundsOwner.features.`new context bounds and givens`
      && hasOldStyleContextBounds(boundsOwner) =>
      holder.registerProblem(boundsOwner, description, new ConvertToNewStyleAggregateBoundsQuickFix(boundsOwner))
    case _ =>
  }

  private def hasOldStyleContextBounds(owner: ScTypeBoundsOwner): Boolean =
    owner.contextBounds.headOption.exists(cb => PsiTreeUtil.skipWhitespacesBackward(cb).textMatches(":"))

  private final class ConvertToNewStyleAggregateBoundsQuickFix(owner: ScTypeBoundsOwner)
    extends AbstractFixOnPsiElement(fixDescription, owner)
      with DumbAware {
    override protected def doApplyFix(owner: ScTypeBoundsOwner)
                                     (implicit project: Project): Unit = {
      val bounds = owner.contextBounds
      val comma  = ScalaPsiElementFactory.createComma(owner)
      val lbrace = ScalaPsiElementFactory.createLBrace(owner)
      val rbrace = ScalaPsiElementFactory.createRBrace(owner)

      bounds.headOption.foreach(cb => owner.addBefore(lbrace, cb))
      bounds.tail.foreach(cb => PsiTreeUtil.skipWhitespacesBackward(cb).replace(comma))
      bounds.lastOption.foreach(cb => owner.addAfter(rbrace, cb))
    }
  }
}

object OldStyleAggregateContextBoundsInspection {
  @Nls
  val description: String = ScalaInspectionBundle.message("displayname.old.style.aggregate.context.bounds.are.deprecated")
  @Nls
  val fixDescription: String = ScalaInspectionBundle.message("replace.with.new.syntax.for.aggregate.bounds")
}
