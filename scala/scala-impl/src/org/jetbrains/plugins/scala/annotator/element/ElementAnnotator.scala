package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationHolder, ScopeAnnotator}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.ArraySeq

abstract class ElementAnnotator[T: reflect.ClassTag] {

  def annotate(element: T, typeAware: Boolean)
              (implicit holder: ScalaAnnotationHolder): Unit

  private def doAnnotate(element: ScalaPsiElement, typeAware: Boolean)
                        (implicit holder: ScalaAnnotationHolder): Boolean = element match {
    case element: T =>
      annotate(element, typeAware)
      true
    case _ =>
      false
  }
}

object ElementAnnotator extends ElementAnnotator[ScalaPsiElement] {

  private val Instances =
    ScAnnotationAnnotator ::
      ScAssignmentAnnotator ::
      ScBlockExprAnnotator ::
      ScCatchBlockAnnotator ::
      ScCharLiteralAnnotator ::
      ScClassAnnotator ::
      ScConstrBlockExprAnnotator ::
      ScConstructorInvocationAnnotator ::
      ScExpressionAnnotator ::
      ScEnumeratorsAnnotator ::
      ScForAnnotator ::
      ScForBindingAnnotator ::
      ScGenericCallAnnotator ::
      ScImportExprAnnotator ::
      ScInterpolatedStringLiteralAnnotator ::
      ScStringLiteralAnnotator ::
      ScLongLiteralAnnotator ::
      ScIntegerLiteralAnnotator ::
      ScLiteralTypeElementAnnotator ::
      ScMethodInvocationAnnotator ::
      ScNewTemplateDefinitionAnnotator ::
      ScFunctionAnnotator ::
      ScFunctionExprAnnotator ::
      ScParameterAnnotator ::
      ScParameterizedTypeElementAnnotator ::
      ScParametersAnnotator ::
      ScPatternArgumentListAnnotator ::
      ScPatternAnnotator ::
      ScPatternTypeUnawareAnnotator ::
      ScPrefixExprAnnotator ::
      ScValueOrVariableAnnotator ::
      ScReferenceAnnotator ::
      ScReturnAnnotator ::
      ScSelfInvocationAnnotator ::
      ScSimpleTypeElementAnnotator ::
      ScTemplateDefinitionAnnotator ::
      ScTraitAnnotator ::
      ScBoundsOwnerAnnotator ::
      ScTypedExpressionAnnotator ::
      ScUnderscoreSectionAnnotator ::
      ScVariableDeclarationAnnotator ::
      ScopeAnnotator ::
      ScSymbolLiteralAnnotator ::
      ScFunctionalTypeElementAnnotator ::
      ScMacroDefAnnotator ::
      ScEnumCaseAnnotator ::
      ScPolyFunctionTypeElementAnnotator ::
      ScPolyFunctionExprAnnotator ::
      ScDerivesClauseAnnotator ::
      ScOverriddenVarAnnotator ::
      ScGivenAliasDeclarationAnnotator ::
      ScMemberAnnotator ::
      ScExportStmtAnnotator ::
      Nil

  private val cachedAnnotators: AtomicReference[Map[Class[_], Seq[ElementAnnotator[_]]]] =
    new AtomicReference(Map.empty)

  override def annotate(element: ScalaPsiElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val mapping = cachedAnnotators.get()
    val clazz = element.getClass

    mapping.get(clazz) match {
      case Some(annotators) =>
        annotators.foreach(_.doAnnotate(element, typeAware))
      case None =>
        val annotators =
          Instances.filter(_.doAnnotate(element, typeAware)).to(ArraySeq)

        cachedAnnotators.getAndUpdate(_.updated(clazz, annotators))
    }
  }
}
