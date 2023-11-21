package org.jetbrains.plugins.scala.codeInspection.notImplementedCode

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.TemplateUtils
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._

class NotImplementedCodeInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case reference @ ReferenceTarget(Member("???", "scala.Predef")) =>
      holder.registerProblem(reference, ScalaInspectionBundle.message("not.implemented"), new ImplementQuickFix(reference))
    case _ =>
  }

  private class ImplementQuickFix(e: PsiElement) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("implement.quickfix.name"), e) {

    override protected def doApplyFix(elem: PsiElement)
                                     (implicit project: Project): Unit = {
      val builder = new TemplateBuilderImpl(elem)
      builder.replaceElement(elem, elem.getText)
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(elem)
      val template = builder.buildTemplate()
      //NOTE: unfortunately we don't have access to the original editor in this quick fix
      // Thus when the quick fix is invoked in Scala REPL, a new editor will be opened (like in SCL-3750)
      TemplateUtils.positionCursorAndStartTemplate(elem, template, originalEditor = None)
    }
  }
}
