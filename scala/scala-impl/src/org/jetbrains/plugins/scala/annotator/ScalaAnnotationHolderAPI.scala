package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Methods similar to the deprecated API of [[com.intellij.lang.annotation.AnnotationHolder]],
 * implemented using new API
 */
trait ScalaAnnotationHolderAPI {
  this: ScalaAnnotationHolder =>

  final def createErrorAnnotation(range: TextRange,
                                  @InspectionMessage message: String,
                                  highlightType: ProblemHighlightType,
                                  fixes: Iterable[CommonIntentionAction]): Unit = {
    val builder = newAnnotation(HighlightSeverity.ERROR, message: String)
      .range(range)
      .highlightType(highlightType)
    for (fix <- fixes) {
      builder.withFix(fix)
    }
    builder.create()
  }

  final def createErrorAnnotation(element: PsiElement,
                                  @InspectionMessage message: String,
                                  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR,
                                  fixes: Iterable[CommonIntentionAction] = Nil): Unit =
    createErrorAnnotation(element.getTextRange, message, highlightType, fixes)


  final def createErrorAnnotation(element: PsiElement,
                                  @InspectionMessage message: String,
                                  fix: CommonIntentionAction): Unit =
    createErrorAnnotation(element.getTextRange, message, ProblemHighlightType.GENERIC_ERROR, Some(fix))

  final def createErrorAnnotation(range: TextRange,
                                  @InspectionMessage message: String,
                                  fixes: Iterable[CommonIntentionAction]): Unit =
    createErrorAnnotation(range, message, ProblemHighlightType.GENERIC_ERROR, fixes)

  final def createErrorAnnotation(element: PsiElement,
                                  @InspectionMessage message: String,
                                  fixes: Iterable[CommonIntentionAction]): Unit =
    createErrorAnnotation(element, message, ProblemHighlightType.GENERIC_ERROR, fixes)

  final def createErrorAnnotation(range: TextRange,
                                  @InspectionMessage message: String,
                                  fix: CommonIntentionAction): Unit =
    createErrorAnnotation(range, message, ProblemHighlightType.GENERIC_ERROR, Some(fix))

  final def createErrorAnnotation(range: TextRange,
                                  @InspectionMessage message: String): Unit =
    createErrorAnnotation(range, message, ProblemHighlightType.GENERIC_ERROR, None)

  final def createWarningAnnotation(range: TextRange,
                                    @InspectionMessage message: String,
                                    highlightType: ProblemHighlightType,
                                    fixes: Iterable[CommonIntentionAction]): Unit = {
    val builder = newAnnotation(HighlightSeverity.WARNING, message: String)
      .range(range)
      .highlightType(highlightType)

    for (f <- fixes) {
      builder.withFix(f)
    }
    builder.create()
  }

  final def createWarningAnnotation(element: PsiElement,
                                    @InspectionMessage message: String,
                                    highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    fixes: Iterable[CommonIntentionAction] = Nil): Unit =
    createWarningAnnotation(element.getTextRange, message, highlightType, fixes)

  final def createWarningAnnotation(element: PsiElement,
                                    @InspectionMessage message: String,
                                    highlightType: ProblemHighlightType,
                                    fix: CommonIntentionAction): Unit =
    createWarningAnnotation(element.getTextRange, message, highlightType, Some(fix))

  final def createWarningAnnotation(element: PsiElement,
                                    @InspectionMessage message: String,
                                    fix: CommonIntentionAction): Unit =
    createWarningAnnotation(element.getTextRange, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, Some(fix))


  final def createInfoAnnotation(range: TextRange,
                                 @InspectionMessage message: String,
                                 enforcedAttributes: Option[TextAttributes],
                                 fixes: Iterable[CommonIntentionAction]): Unit = {
    val builder = newAnnotation(HighlightSeverity.INFORMATION, message: String)
      .range(range)
    for (fix <- fixes) {
      builder.withFix(fix)
    }
    for (attr <- enforcedAttributes) {
      builder.enforcedTextAttributes(attr)
    }
    builder.create()
  }

  final def createWeakWarningAnnotation(element: PsiElement,
                                        @InspectionMessage message: String): Unit =
    newAnnotation(HighlightSeverity.WEAK_WARNING, message)
      .range(element)
      .create()
}
