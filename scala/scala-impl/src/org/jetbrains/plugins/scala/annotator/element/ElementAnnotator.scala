package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

abstract class ElementAnnotator[T: reflect.ClassTag] {

  def annotate(element: T, typeAware: Boolean)
              (implicit holder: ScalaAnnotationHolder): Unit

  private def doAnnotate(element: ScalaPsiElement, typeAware: Boolean)
                        (implicit holder: ScalaAnnotationHolder): Unit = element match {
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
      ScConstrBlockExprAnnotator ::
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
      ScFunctionExprAnnotator ::
      ScParameterAnnotator ::
      ScParameterizedTypeElementAnnotator ::
      ScParametersAnnotator ::
      ScPatternArgumentListAnnotator ::
      ScPatternAnnotator ::
      ScPatternTypeUnawareAnnotator ::
      ScValueOrVariableAnnotator ::
      ScReferenceAnnotator ::
      ScReturnAnnotator ::
      ScSelfInvocationAnnotator ::
      ScSimpleTypeElementAnnotator ::
      ScTemplateDefinitionAnnotator ::
      ScTraitAnnotator ::
      ScTypeBoundsOwnerAnnotator ::
      ScTypedExpressionAnnotator ::
      ScUnderscoreSectionAnnotator ::
      ScVariableDeclarationAnnotator ::
      ScopeAnnotator ::
      ScSymbolLiteralAnnotator ::
      ScFunctionalTypeElementAnnotator ::
      ScMacroDefAnnotator ::
      ScEnumCaseAnnotator ::
      Nil

  override def annotate(element: ScalaPsiElement, typeAware: Boolean = true)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    Instances.foreach {
      _.doAnnotate(element, typeAware)
    }
}
