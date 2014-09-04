package org.jetbrains.plugins.scala
package codeInspection
package scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocInlinedTag

/**
 * User: Dmitry Naidanov
 * Date: 11/21/11
 */

class ScalaDocInlinedTagInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = "Inlined Tag"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitInlinedTag(s: ScDocInlinedTag) {
        holder.registerProblem(holder.getManager.createProblemDescriptor(s, getDisplayName, true,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, new ScalaDocInlinedTagDeleteQuickFix(s),
          new ScalaDocInlinedTagReplaceQuickFix(s)))
      }
    }
  }
}


class ScalaDocInlinedTagDeleteQuickFix(inlinedTag: ScDocInlinedTag) extends LocalQuickFix {
  def getName: String = "Delete Inlined Tag"

  def getFamilyName: String = InspectionsUtil.SCALADOC

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!inlinedTag.isValid) return
    inlinedTag.delete()
  }
}

class ScalaDocInlinedTagReplaceQuickFix(inlinedTag: ScDocInlinedTag) extends LocalQuickFix {
  def getName: String = "Replace inlined tag with monospace wiki syntax"

  def getFamilyName: String = InspectionsUtil.SCALADOC

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!inlinedTag.isValid) return

    if (inlinedTag.getValueElement == null) {
      inlinedTag.replace(ScalaPsiElementFactory.createMonospaceSyntaxFromText("", inlinedTag.getManager))  
    } else{
      val tagText =
        inlinedTag.getValueElement.getText.replace("`", MyScaladocParsing.escapeSequencesForWiki.get("`").get)
      inlinedTag.replace(ScalaPsiElementFactory.createMonospaceSyntaxFromText(tagText, inlinedTag.getManager))
    }
  }
}