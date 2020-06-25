package org.jetbrains.plugins.scala.codeInsight.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr

/**
  * @author Pavel Fatin
  */
class InspectionBasedIntention(@Nls family: String, @Nls text: String, inspection: LocalInspectionTool) extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = family

  override def getText: String = text

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    findProblemFrom(element).foreach { descriptor =>
      val fixes = descriptor.getFixes

      if (fixes.nonEmpty) {
        val fix = fixes.apply(0).asInstanceOf[LocalQuickFix]
        fix.applyFix(project, descriptor)
      }
    }
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = findProblemFrom(element).isDefined

  private def findProblemFrom(element: PsiElement): Option[ProblemDescriptor] = {
    val holder = new ProblemsHolder(InspectionManager.getInstance(element.getProject), element.getContainingFile, true)
    val visitor = inspection.buildVisitor(holder, true)
    var e = element
    do {
      visitor.visitElement(e)
      e = e.getParent
    } while (holder.getResultCount == 0 && e != null && !e.isInstanceOf[ScBlockExpr])
    if (holder.getResultCount > 0) Some(holder.getResults.get(0)) else None
  }
}
