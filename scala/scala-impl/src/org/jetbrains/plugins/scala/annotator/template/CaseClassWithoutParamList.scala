package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.ObjectKeyword
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaLanguageLevel}

object CaseClassWithoutParamList extends AnnotatorPart[ScClass] {

  override def annotate(element: ScClass, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    def createAnnotation(nameId: PsiElement, fixes: Iterable[IntentionAction]) = {
      if (element.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_2_11)) {
        val message = ScalaBundle.message("case.classes.without.parameter.list.not.allowed")
        holder.createErrorAnnotation(nameId, message, fixes)
      }
      else {
        val message = ScalaBundle.message("case.classes.without.parameter.list.deprecated")
        holder.createWarningAnnotation(nameId, message, ProblemHighlightType.LIKE_DEPRECATED, fixes)
      }
    }

    if (element.isCase && !element.clauses.exists(_.clauses.nonEmpty)) {
      val nameId = element.nameId
      val fixes = Seq(new ConvertToObjectFix(element), new AddEmptyParenthesesToPrimaryConstructorFix(element))
      createAnnotation(nameId, fixes)
    }
  }
}

class AddEmptyParenthesesToPrimaryConstructorFix(c: ScClass) extends IntentionAction {

  override def getFamilyName: String = ScalaBundle.message("family.name.add.empty.parentheses")

  override def getText: String = getFamilyName

  override def startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    c.isValid

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit =
    c.clauses foreach {
      _.addClause(createClauseFromText()(c.getManager))
    }
}

final class ConvertToObjectFix(c: ScClass) extends IntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.convert.to.object")

  override def getText: String = getFamilyName

  override def startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    c.isValid

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val classKeywordTextRange = c.targetToken.getTextRange

    val objectText = c.getText.patch(
      classKeywordTextRange.getStartOffset - c.getTextRange.getStartOffset,
      ObjectKeyword.text,
      classKeywordTextRange.getLength
    )

    val objectElement = ScalaPsiElementFactory.createObjectWithContext(objectText, c.getContext, c)
    c.replace(objectElement)
    // TODO update references to class.
    // new X  -> X
    // x: X   -> x: X.type
  }
}
