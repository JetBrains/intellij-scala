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


class ScalaDocInlinedTagDeleteQuickFix(inlinedTag: ScDocInlinedTag)
        extends AbstractFixOnPsiElement(ScalaBundle.message("delete.inlined.tag"), inlinedTag) {
  override def getFamilyName: String = InspectionsUtil.SCALADOC

  def doApplyFix(project: Project) {
    val tag = getElement
    if (!tag.isValid) return
    tag.delete()
  }
}

class ScalaDocInlinedTagReplaceQuickFix(inlinedTag: ScDocInlinedTag)
        extends AbstractFixOnPsiElement(ScalaBundle.message("replace.with.wiki.syntax"), inlinedTag) {
  override def getFamilyName: String = InspectionsUtil.SCALADOC

  def doApplyFix(project: Project) {
    val tag = getElement
    if (!tag.isValid) return

    if (tag.getValueElement == null) {
      tag.replace(ScalaPsiElementFactory.createMonospaceSyntaxFromText("", tag.getManager))
    } else{
      val tagText =
        tag.getValueElement.getText.replace("`", MyScaladocParsing.escapeSequencesForWiki.get("`").get)
      tag.replace(ScalaPsiElementFactory.createMonospaceSyntaxFromText(tagText, tag.getManager))
    }
  }
}