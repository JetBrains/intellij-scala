package org.jetbrains.plugins.scala
package codeInspection
package scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.apache.commons.lang.StringUtils
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocSyntaxElement

/**
 * User: Dmitry Naidanov
 * Date: 11/21/11
 */

class ScalaDocUnclosedTagWithoutParserInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = "Unclosed Tag"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitWikiSyntax(s: ScDocSyntaxElement) {
        val firstElementType = s.getFirstChild.getNode.getElementType
        if (!ScaladocSyntaxElementType.canClose(firstElementType,
          s.getLastChild.getNode.getElementType) &&
          firstElementType != ScalaDocTokenType.DOC_HEADER && firstElementType != ScalaDocTokenType.VALID_DOC_HEADER) {

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