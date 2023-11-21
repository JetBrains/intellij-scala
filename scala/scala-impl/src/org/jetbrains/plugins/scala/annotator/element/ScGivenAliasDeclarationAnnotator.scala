package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInsight.intention.{FileModifier, IntentionAction}
import com.intellij.codeInsight.template.{TemplateBuilderImpl, TemplateManager}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationHolder, TemplateUtils}
import org.jetbrains.plugins.scala.codeInsight.intention.types.ChooseValueExpression
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenAliasDeclaration, ScGivenAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createPsiElementFromText
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaVariableValidator

object ScGivenAliasDeclarationAnnotator extends ElementAnnotator[ScGivenAliasDeclaration] {
  override def annotate(decl: ScGivenAliasDeclaration, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    checkAnonymousGivenDeclaration(decl)

  private def checkAnonymousGivenDeclaration(decl: ScGivenAliasDeclaration)
                                            (implicit holder: ScalaAnnotationHolder): Unit =
    decl.nameElement match {
      case None =>
        val fixes =
          new ImplementAnonymousAbstractGivenFix(decl) ::
            new NameAnonymousAbstractGivenFix(decl) ::
            Nil

        holder.createErrorAnnotation(decl, ScalaBundle.message("given.alias.declaration.must.be.named"), fixes)
      case _ =>
    }
}

private[element] abstract class AnonymousGivenAliasDeclarationQuickFix(declaration: ScGivenAliasDeclaration) extends IntentionAction {
  override def startInWriteAction(): Boolean = true

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    declaration.isValid && declaration.nameElement.isEmpty

  final override def invoke(project: Project, editor: Editor, file: PsiFile): Unit =
    doInvoke(editor, project)

  protected def doInvoke(implicit editor: Editor, project: Project): Unit

  protected def runTemplate(builder: TemplateBuilderImpl, context: PsiElement)
                           (implicit editor: Editor, project: Project): Unit = {
    val template = builder.buildTemplate()
    TemplateUtils.startTemplateAtElement(editor, template, context)
  }
}

private[element] final class ImplementAnonymousAbstractGivenFix(declaration: ScGivenAliasDeclaration)
  extends AnonymousGivenAliasDeclarationQuickFix(declaration) {
  override def getFamilyName: String = ScalaBundle.message("family.name.implement.anonymous.abstract.given")

  override def getFileModifierForPreview(target: PsiFile): FileModifier =
    new ImplementAnonymousAbstractGivenFix(PsiTreeUtil.findSameElementInCopy(declaration, target))

  override protected def doInvoke(implicit editor: Editor, project: Project): Unit = {
    val newElement = declaration.replace(createPsiElementFromText(s"${declaration.getText} = ???", features = declaration))

    if (!IntentionPreviewUtils.isIntentionPreviewActive) {
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newElement) match {
        case definition: ScGivenAliasDefinition =>
          definition.body.foreach { body =>
            val builder = new TemplateBuilderImpl(body)
            builder.replaceElement(body, body.getText)
            runTemplate(builder, body)
          }
        case _ =>
      }
    }
  }
}

private[element] final class NameAnonymousAbstractGivenFix(declaration: ScGivenAliasDeclaration)
  extends AnonymousGivenAliasDeclarationQuickFix(declaration) {
  override def getFamilyName: String = ScalaBundle.message("family.name.give.a.name.to.anonymous.abstract.given")

  override def getFileModifierForPreview(target: PsiFile): FileModifier =
    new NameAnonymousAbstractGivenFix(PsiTreeUtil.findSameElementInCopy(declaration, target))

  override protected def doInvoke(implicit editor: Editor, project: Project): Unit = {
    val typeElement = declaration.typeElement match {
      case Some(te) => te
      case None =>
        return
    }

    val declContext = declaration.getContext
    val validator = new ScalaVariableValidator(declaration, false, declContext, declContext)
    val suggestedNames =
      NameSuggester.suggestNames(declaration, validator)
        .pipeIf(ApplicationManager.getApplication.isUnitTestMode)(_.sorted.reverse)
    val firstName = suggestedNames.head

    editor.getDocument.insertString(typeElement.getTextRange.getStartOffset, firstName + ": ")

    if (!IntentionPreviewUtils.isIntentionPreviewActive) {
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(declaration) match {
        case decl: ScGivenAliasDeclaration =>
          decl.nameElement.foreach { nameElement =>
            val builder = new TemplateBuilderImpl(nameElement)
            builder.replaceElement(nameElement, new ChooseStringValueExpression(suggestedNames, firstName))
            runTemplate(builder, nameElement)
          }
        case _ =>
      }
    }
  }

  private class ChooseStringValueExpression(items: Seq[String], defaultItem: String)
    extends ChooseValueExpression(items, defaultItem) {
    override def lookupString(element: String): String = element

    override def result(element: String): String = element
  }
}
