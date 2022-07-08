package org.jetbrains.plugins.scala
package codeInspection.xml

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.{ScXmlEndTag, ScXmlStartTag}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

class ScalaXmlUnmatchedTagInspection extends LocalInspectionTool{
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitXmlStartTag(s: ScXmlStartTag): Unit = {
        if (s.getTextRange.isEmpty) return

        val endTag = s.getClosingTag
        def register(fixes: LocalQuickFix*): Unit = {
          holder.registerProblem(s, ScalaBundle.message("xml.no.closing.tag"), fixes: _*)
        }

        if (endTag == null) {
          register(new DeleteUnmatchedTagQuickFix(s))
        } else if (endTag.getTagName != s.getTagName) {
          register(new DeleteUnmatchedTagQuickFix(s), new RenameClosingTagQuickFix(s))
        }
      }

      override def visitXmlEndTag(s: ScXmlEndTag): Unit = {
        val startTag = s.getOpeningTag
        def register(fixes: LocalQuickFix*): Unit = {
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

class DeleteUnmatchedTagQuickFix(s: ScalaPsiElement)
        extends AbstractFixOnPsiElement(ScalaBundle.message("xml.delete.unmatched.tag"), s) {

  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(elem: ScalaPsiElement)
                                   (implicit project: Project): Unit = {
    elem.delete()
  }
}

class RenameClosingTagQuickFix(s: ScXmlStartTag)
        extends AbstractFixOnPsiElement(ScalaBundle.message("xml.rename.closing.tag"), s) {

  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(elem: ScXmlStartTag)
                                   (implicit project: Project): Unit = {
    elem.getClosingTag.replace(createXmlEndTag(elem.getTagName))
  }
}

class RenameOpeningTagQuickFix(s: ScXmlEndTag)
        extends AbstractFixOnPsiElement(ScalaBundle.message("xml.rename.opening.tag"), s) {

  override def getFamilyName: String = FamilyName


  override protected def doApplyFix(elem: ScXmlEndTag)
                                   (implicit project: Project): Unit = {
    val openingTag = elem.getOpeningTag
    val attributes = openingTag.findChildrenByType(ScalaElementType.XML_ATTRIBUTE).map(_.getText)

    elem.getOpeningTag.replace(createXmlStartTag(elem.getTagName,
      if (attributes.isEmpty) "" else attributes.mkString(" ", " ", "")))
  }
}


