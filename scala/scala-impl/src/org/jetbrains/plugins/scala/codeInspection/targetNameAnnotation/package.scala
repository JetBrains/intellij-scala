package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

package object targetNameAnnotation {
  val TargetNameAnnotationFQN = "scala.annotation.targetName"

  def targetNameAnnotationWithParamFQN(extName: String): String = s"""$TargetNameAnnotationFQN("$extName")"""

  def hasTargetNameAnnotation(element: ScAnnotationsHolder): Boolean =
    element.hasAnnotation(TargetNameAnnotationFQN)

  private[targetNameAnnotation] def targetNameAnnotationExternalName(annotation: ScAnnotation): Option[String] =
    for {
      extNameExpr <- annotation.annotationExpr.getAnnotationParameters.headOption
      extNameLiteral <- extNameExpr.asOptionOf[ScStringLiteral]
    } yield extNameLiteral.getValue

  private[targetNameAnnotation] def lastTargetNameAnnotation(element: ScAnnotationsHolder): Option[ScAnnotation] =
    element.annotations(TargetNameAnnotationFQN).lastOption

  private[targetNameAnnotation] def targetNameAnnotationExternalName(element: ScAnnotationsHolder): Option[String] =
    lastTargetNameAnnotation(element).flatMap(targetNameAnnotationExternalName)

  private def addAnnotation(element: ScAnnotationsHolder, annotationText: String): ScAnnotation =
    element.addAnnotation(annotationText, addNewLine = !element.is[ScParameter])

  /**
   * When we override a member which has a `@targetName("someName")` annotation
   * an overriding member must also have the same annotation with the same name, otherwise it won't compile
   */
  def addTargetNameAnnotationIfNeeded(element: ScAnnotationsHolder, superElementObject: AnyRef): Unit =
    if (element.isInScala3File) {
      superElementObject match {
        case superElement: ScAnnotationsHolder if hasTargetNameAnnotation(superElement) && !hasTargetNameAnnotation(element) =>
          targetNameAnnotationExternalName(superElement)
            .foreach { extName =>
              addAnnotation(element, targetNameAnnotationWithParamFQN(extName))
            }
        case _ =>
      }
    }

  class AddTargetNameAnnotationQuickFix(element: ScAnnotationsHolder, extName: String = "")
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("add.targetname.annotation"), element) {
    private val annotationText = targetNameAnnotationWithParamFQN(extName)

    override protected def doApplyFix(element: ScAnnotationsHolder)(implicit project: Project): Unit = {
      val annotation = addAnnotation(element, annotationText)

      if (extName.isEmpty) {
        for {
          templateContainerElement <- annotation.annotationExpr.getAnnotationParameters.headOption
          editor <- templateContainerElement.openTextEditor
        } runTemplate(editor, templateContainerElement)
      }
    }

    private def runTemplate(editor: Editor, templateContainerElement: PsiElement)(implicit project: Project): Unit = {
      val document = editor.getDocument
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)

      val builder = TemplateBuilderFactory.getInstance()
        .createTemplateBuilder(templateContainerElement)
      builder.replaceElement(templateContainerElement, TextRange.EMPTY_RANGE.shiftRight(1), "")
      builder.run(editor, false)
    }
  }

  object TargetNameAnnotationWithOwner {
    def unapply(annotation: ScAnnotation): Option[(ScAnnotation, ScModifierListOwner)] =
      if (annotation.hasQualifiedName(TargetNameAnnotationFQN))
        annotation.findContextOfType(classOf[ScModifierListOwner]).map((annotation, _))
      else None
  }

  private[targetNameAnnotation] final case class ProblemElement(element: PsiElement,
                                                                maybeQuickFix: Option[LocalQuickFix] = None,
                                                                maybeDescription: Option[String] = None)

  private[targetNameAnnotation] object ProblemElement {
    def apply(element: PsiElement, description: String) =
      new ProblemElement(element, maybeDescription = Some(description))

    def apply(element: PsiElement, quickFix: LocalQuickFix, description: String) =
      new ProblemElement(element, maybeQuickFix = Some(quickFix), maybeDescription = Some(description))
  }
}
