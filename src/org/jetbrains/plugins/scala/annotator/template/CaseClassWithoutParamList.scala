package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

object CaseClassWithoutParamList extends AnnotatorPart[ScClass] {
  def kind: Class[ScClass] = classOf[ScClass]

  def annotate(element: ScClass, holder: AnnotationHolder, typeAware: Boolean) {
    if (element.isCase && !element.clauses.exists(_.clauses.nonEmpty)) {
      val nameId = element.nameId
      val annotation = holder.createWarningAnnotation(nameId, "case classes without a parameter list have been deprecated")
      annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
      val fixes = Seq(new ConvertToObjectFix(element), new AddEmptyParenthesesToPrimaryConstructorFix(element))
      fixes.foreach(fix => annotation.registerFix(fix, nameId.getTextRange))
    }
  }
}

class AddEmptyParenthesesToPrimaryConstructorFix(c: ScClass) extends IntentionAction {
  def getText: String = "Add empty parentheses"

  def getFamilyName: String = getText

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = c.isValid && c.getManager.isInProject(file)

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    c.addEmptyParens()
  }
}

class ConvertToObjectFix(c: ScClass) extends IntentionAction {
  def getText: String = "Convert to object"

  def getFamilyName: String = getText

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = c.isValid && c.getManager.isInProject(file)

  def invoke(project: Project, editor: Editor, file: PsiFile) {
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
