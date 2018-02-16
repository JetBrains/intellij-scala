package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaLanguageLevel}

object CaseClassWithoutParamList extends AnnotatorPart[ScClass] {
  def annotate(element: ScClass, holder: AnnotationHolder, typeAware: Boolean) {
    def createAnnotation(nameId: PsiElement) = {
      if (element.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_2_11)) {
        val message = "case classes without a parameter list are not allowed"
        holder.createErrorAnnotation(nameId, message)
      }
      else {
        val message = "case classes without a parameter list have been deprecated"
        val deprAnnot = holder.createWarningAnnotation(nameId, message)
        deprAnnot.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
        deprAnnot
      }
    }

    if (element.isCase && !element.clauses.exists(_.clauses.nonEmpty)) {
      val nameId = element.nameId
      val annotation = createAnnotation(nameId)
      val fixes = Seq(new ConvertToObjectFix(element), new AddEmptyParenthesesToPrimaryConstructorFix(element))
      fixes.foreach(fix => annotation.registerFix(fix, nameId.getTextRange))
    }
  }
}

class AddEmptyParenthesesToPrimaryConstructorFix(c: ScClass) extends IntentionAction {

  override def getText: String = "Add empty parentheses"

  override def getFamilyName: String = getText

  override def startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    c.isValid && c.getManager.isInProject(file)

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit =
    c.clauses foreach {
      _.addClause(createClauseFromText("()")(c.getManager))
    }
}

class ConvertToObjectFix(c: ScClass) extends IntentionAction {
  override def getText: String = "Convert to object"

  override def getFamilyName: String = getText

  override def startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    c.isValid && c.getManager.isInProject(file)

  override def invoke(project: Project, editor: Editor, file: PsiFile) {
    val classKeywordTextRange = c.getClassToken.getTextRange
    val classTextRange = c.getTextRange
    val start = classKeywordTextRange.getStartOffset - classTextRange.getStartOffset
    val charsToReplace = classKeywordTextRange.getLength
    val classText = c.getText
    val objectText = classText.patch(start, "object", charsToReplace)
    val objectElement = ScalaPsiElementFactory.createObjectWithContext(objectText, c.getContext, c)
    c.replace(objectElement)
    // TODO update references to class.
    // new X  -> X
    // x: X   -> x: X.type
  }
}
