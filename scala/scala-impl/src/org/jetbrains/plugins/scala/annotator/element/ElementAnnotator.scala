package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

abstract class ElementAnnotator[T: reflect.ClassTag] {

  def annotate(element: T, typeAware: Boolean)
              (implicit holder: AnnotationHolder): Unit

  private def doAnnotate(element: ScalaPsiElement, typeAware: Boolean)
                        (implicit holder: AnnotationHolder): Unit = element match {
    case element: T => annotate(element, typeAware)
    case _ =>
  }
}

object ElementAnnotator extends ElementAnnotator[ScalaPsiElement] {

  private val Instances =
    ScAnnotationAnnotator ::
      ScAssignmentAnnotator ::
      ScCatchBlockAnnotator ::
      ScClassAnnotator ::
      ScConstrBlockAnnotator ::
      ScConstructorInvocationAnnotator ::
      ScExpressionAnnotator ::
      ScEnumeratorsAnnotator ::
      ScForBindingAnnotator ::
      ScGeneratorAnnotator ::
      ScImportExprAnnotator ::
      ScInterpolatedStringLiteralAnnotator ::
      ScStringLiteralAnnotator ::
      ScLongLiteralAnnotator ::
      ScIntegerLiteralAnnotator ::
      ScLiteralTypeElementAnnotator ::
      ScMethodCallAnnotator ::
      ScMethodInvocationAnnotator ::
      ScNewTemplateDefinitionAnnotator ::
      ScParameterAnnotator ::
      ScParameterizedTypeElementAnnotator ::
      ScParametersAnnotator ::
      ScPatternAnnotator ::
      ScPatternDefinitionAnnotator ::
      ScReferenceAnnotator ::
      ScReturnAnnotator ::
      ScSelfInvocationAnnotator ::
      ScSimpleTypeElementAnnotator ::
      ScTemplateDefinitionAnnotator ::
      ScTypeBoundsOwnerAnnotator ::
      ScTypedExpressionAnnotator ::
      ScUnderscoreSectionAnnotator ::
      ScVariableDeclarationAnnotator ::
      ScopeAnnotator ::
      ScSymbolLiteralAnnotator ::
      ScFunctionalTypeElementAnnotator ::
      Nil

  override def annotate(element: ScalaPsiElement, typeAware: Boolean = true)
                       (implicit holder: AnnotationHolder): Unit =
    Instances.foreach {
      _.doAnnotate(element, typeAware)
    }
}
