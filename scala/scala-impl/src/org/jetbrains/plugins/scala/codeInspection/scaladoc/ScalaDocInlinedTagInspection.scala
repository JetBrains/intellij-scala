package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocInlinedTag

final class ScalaDocInlinedTagInspection extends LocalInspectionTool with DumbAware {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = ScalaInspectionBundle.message("display.name.inlined.tag")

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    new ScalaElementVisitor {
      override def visitInlinedTag(inlinedTag: ScDocInlinedTag): Unit = {
        val problem = holder.getManager.createProblemDescriptor(
          inlinedTag, getDisplayName, true,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly,
          new ScalaDocInlinedTagDeleteQuickFix(inlinedTag),
          new ScalaDocInlinedTagReplaceQuickFix(inlinedTag)
        )
        holder.registerProblem(problem)
      }
    }
}

final class ScalaDocInlinedTagDeleteQuickFix(inlinedTag: ScDocInlinedTag)
  extends AbstractFixOnPsiElement(ScalaBundle.message("delete.inlined.tag"), inlinedTag)
    with DumbAware {
  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(tag: ScDocInlinedTag)(implicit project: Project): Unit =
    tag.delete()
}

final class ScalaDocInlinedTagReplaceQuickFix(inlinedTag: ScDocInlinedTag)
  extends AbstractFixOnPsiElement(ScalaBundle.message("replace.with.wiki.syntax"), inlinedTag)
    with DumbAware {
  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(tag: ScDocInlinedTag)(implicit project: Project): Unit = {
    val textNew = tag.valueText.map(escaped).getOrElse("")
    val elementNew = tag.name match {
      case "code" =>
        ScalaPsiElementFactory.createScalaDocSimpleData(s"<pre>$textNew</pre>")
      case "link" | "linkplain" =>
        ScalaPsiElementFactory.createScalaDocLinkSyntaxFromText(textNew)
      case _ =>
        ScalaPsiElementFactory.createScalaDocMonospaceSyntaxFromText(textNew)
    }
    tag.replace(elementNew)
  }

  private def escaped(text: String): String =
    MyScaladocParsing.escapeSequencesForWiki.foldLeft(text) {
      case (acc, (from, to)) =>
        acc.replace(from, to)
    }
}
