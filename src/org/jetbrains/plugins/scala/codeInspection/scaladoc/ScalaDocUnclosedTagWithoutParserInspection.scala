package org.jetbrains.plugins.scala
package codeInspection
package scaladoc

import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocSyntaxElement
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import com.intellij.psi.PsiElementVisitor
import com.intellij.openapi.project.Project
import com.intellij.codeInspection._
import org.apache.commons.lang.StringUtils
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange

/**
 * User: Dmitry Naidanov
 * Date: 11/21/11
 */

class ScalaDocUnclosedTagWithoutParserInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitWikiSyntax(s: ScDocSyntaxElement) {
        if (!ScaladocSyntaxElementType.canClose(s.getFirstChild.getNode.getElementType,
          s.getLastChild.getNode.getElementType)) {

          holder.registerProblem(holder.getManager.createProblemDescriptor(s.getFirstChild, getDisplayName, true,
            ProblemHighlightType.GENERIC_ERROR, isOnTheFly, new ScalaDocEscapeTagQuickFix(s)))
        }
      }
    }
  }
}


class ScalaDocEscapeTagQuickFix(s: ScDocSyntaxElement) extends LocalQuickFix {
  def getName: String = "Replace tag with escape sequence"

  def getFamilyName: String = InspectionsUtil.SCALADOC

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!s.isValid) return

    val replaceText = if (s.getFirstChild.getText.contains("=")) {
      StringUtils.repeat(MyScaladocParsing.escapeSequencesForWiki.get("=").get, s.getFirstChild.getText.length())
    } else {
      MyScaladocParsing.escapeSequencesForWiki.get(s.getFirstChild.getText).get
    }
    val doc = FileDocumentManager.getInstance().getDocument(s.getContainingFile.getVirtualFile)
    val range: TextRange = s.getFirstChild.getTextRange
    doc.replaceString(range.getStartOffset, range.getEndOffset, replaceText)
  }
}