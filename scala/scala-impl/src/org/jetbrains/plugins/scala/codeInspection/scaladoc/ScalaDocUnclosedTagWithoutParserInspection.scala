package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocSyntaxElement

final class ScalaDocUnclosedTagWithoutParserInspection extends LocalInspectionTool with DumbAware {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = ScalaInspectionBundle.message("display.name.unclosed.tag")

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitWikiSyntax(s: ScDocSyntaxElement): Unit = {
        val firstElementType = s.getFirstChild.getNode.getElementType
        if (!ScalaDocSyntaxElementType.canClose(firstElementType,
          s.getLastChild.getNode.getElementType) &&
          firstElementType != ScalaDocTokenType.DOC_HEADER && firstElementType != ScalaDocTokenType.VALID_DOC_HEADER) {

          holder.registerProblem(holder.getManager.createProblemDescriptor(s.getFirstChild, getDisplayName, true,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, new ScalaDocEscapeTagQuickFix(s)))
        }
      }
    }
  }
}

final class ScalaDocEscapeTagQuickFix(s: ScDocSyntaxElement)
  extends AbstractFixOnPsiElement(ScalaBundle.message("replace.tag.with.esc.seq"), s)
    with DumbAware {
  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(syntElem: ScDocSyntaxElement)
                                   (implicit project: Project): Unit = {
    val firstChildText = syntElem.getFirstChild.getText
    val replaceText = if (firstChildText.contains("=")) {
      MyScaladocParsing.escapeSequencesForWiki("=") * firstChildText.length()
    } else {
      MyScaladocParsing.escapeSequencesForWiki(firstChildText)
    }
    val doc = syntElem.getContainingFile.getViewProvider.getDocument
    val range: TextRange = syntElem.getFirstChild.getTextRange
    doc.replaceString(range.getStartOffset, range.getEndOffset, replaceText)
  }
}
