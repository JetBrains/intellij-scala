package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.extensions.Resolved
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

/**
 * Nikolay.Tropin
 * 2014-07-28
 */
abstract class CreateFromUsageQuickFixBase(ref: ScReferenceElement, description: String) extends IntentionAction {
  
  val getText = s"Create $description '${ref.nameId.getText}'"

  val getFamilyName = s"Create $description"

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    if (!ref.isValid) return false
    if (!ref.getManager.isInProject(file)) return false
    if (!file.isInstanceOf[ScalaFile]) return false
    if (file.isInstanceOf[ScalaCodeFragment]) return false
    
    true
  }

  override def startInWriteAction() = false

  override def invoke(project: Project, editor: Editor, file: PsiFile) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (!ref.isValid) return

    invokeInner(project, editor, file)
  }

  protected def invokeInner(project: Project, editor: Editor, file: PsiFile)
}

object InstanceOfClass {
  def unapply(elem: PsiElement): Option[PsiClass] = elem match {
    case ScExpression.Type(TypeAsClass(psiClass)) => Some(psiClass)
    case Resolved(typed: ScTypedDefinition, _) =>
      typed.getType().toOption match {
        case Some(TypeAsClass(psiClass)) => Some(psiClass)
        case _ => None
      }
    case _ => None
  }
}

object TypeAsClass {
  def unapply(scType: ScType): Option[PsiClass] = scType match {
    case ScType.ExtractClass(aClass) => Some(aClass)
    case t: ScType => ScType.extractDesignatorSingletonType(t).flatMap(ScType.extractClass(_, None))
    case _ => None
  }
}
