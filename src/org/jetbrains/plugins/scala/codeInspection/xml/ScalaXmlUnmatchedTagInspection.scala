package org.jetbrains.plugins.scala
package codeInspection.xml

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.{ScXmlEndTag, ScXmlStartTag}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory


/**
 * User: Dmitry Naydanov
 * Date: 4/7/12
 */

class ScalaXmlUnmatchedTagInspection extends LocalInspectionTool{
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitXmlStartTag(s: ScXmlStartTag) {
        if (s.getTextRange.isEmpty) return

        val endTag = s.getClosingTag
        def register(fixes: LocalQuickFix*) {
          holder.registerProblem(s, ScalaBundle.message("xml.no.closing.tag"), fixes: _*)
        }

        if (endTag == null) {
          register(new DeleteUnmatchedTagQuickFix(s))
        } else if (endTag.getTagName != s.getTagName) {
          register(new DeleteUnmatchedTagQuickFix(s), new RenameClosingTagQuickFix(s))
        }
      }

      override def visitXmlEndTag(s: ScXmlEndTag) {
        val startTag = s.getOpeningTag
        def register(fixes: LocalQuickFix*) {
          holder.registerProblem(s, ScalaBundle.message("xml.no.opening.tag"), fixes: _*)
        }

        if (startTag == null) {
          register(new DeleteUnmatchedTagQuickFix(s))
        } else if (startTag.getTagName != s.getTagName) {
          register(new DeleteUnmatchedTagQuickFix(s), new RenameOpeningTagQuickFix(s))
        }
      }
    }
  }
}

class DeleteUnmatchedTagQuickFix(s: ScalaPsiElement) extends LocalQuickFix {
  def getName: String = ScalaBundle.message("xml.delete.unmatched.tag")

  def getFamilyName: String = InspectionsUtil.SCALA

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!s.isValid) return

    s.delete()
  }
}

class RenameClosingTagQuickFix(s: ScXmlStartTag) extends LocalQuickFix {
  def getName: String = ScalaBundle.message("xml.rename.closing.tag")

  def getFamilyName: String = InspectionsUtil.SCALA

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!s.isValid) return

    s.getClosingTag.replace(ScalaPsiElementFactory.createXmlEndTag(s.getTagName, s.getManager))
  }
}

class RenameOpeningTagQuickFix(s: ScXmlEndTag) extends LocalQuickFix {
  def getName: String = ScalaBundle.message("xml.rename.opening.tag")

  def getFamilyName: String = InspectionsUtil.SCALA

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!s.isValid) return
    val openingTag = s.getOpeningTag
    val attributes = openingTag.findChildrenByType(ScalaElementTypes.XML_ATTRIBUTE).map(_.getText)

    s.getOpeningTag.replace(ScalaPsiElementFactory.createXmlStartTag(s.getTagName, s.getManager,
      if (attributes.length == 0) "" else attributes.mkString(" ", " ", "")))
  }
}


