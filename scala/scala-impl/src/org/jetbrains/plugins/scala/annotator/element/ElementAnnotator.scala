package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

import scala.reflect.ClassTag

abstract class ElementAnnotator[T: ClassTag] {
  def annotate(element: T, holder: AnnotationHolder, typeAware: Boolean): Unit

  private def doAnnotate(element: ScalaPsiElement, holder: AnnotationHolder, typeAware: Boolean): Unit = element match {
    case it: T => annotate(it, holder, typeAware)
    case _ =>
  }
}

object ElementAnnotator extends ElementAnnotator[ScalaPsiElement] {
  private val All: Seq[ElementAnnotator[_]] = Seq(
    ScAnnotationAnnotator,
    ScAssignmentAnnotator,
    ScCatchBlockAnnotator,
    ScClassAnnotator,
    ScConstrBlockAnnotator,
    ScConstructorInvocationAnnotator,
    ScExpressionAnnotator,
    ScForBindingAnnotator,
    ScGeneratorAnnotator,
    ScImportExprAnnotator,
    ScInterpolatedStringLiteralAnnotator,
    ScLiteralAnnotator,
    ScLiteralTypeElementAnnotator,
    ScMethodCallAnnotator,
    ScMethodInvocationAnnotator,
    ScNewTemplateDefinitionAnnotator,
    ScParameterAnnotator,
    ScParameterizedTypeElementAnnotator,
    ScParametersAnnotator,
    ScPatternAnnotator,
    ScPatternDefinitionAnnotator,
    ScReferenceAnnotator,
    ScReturnAnnotator,
    ScSelfInvocationAnnotator,
    ScSimpleTypeElementAnnotator,
    ScTemplateDefinitionAnnotator,
    ScTypeBoundsOwnerAnnotator,
    ScTypedExpressionAnnotator,
    ScUnderscoreSectionAnnotator,
    ScVariableDeclarationAnnotator,
  )

  def annotate(element: ScalaPsiElement, holder: AnnotationHolder, typeAware: Boolean): Unit =
    All.foreach(_.doAnnotate(element, holder, typeAware))
}
