package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitParametersOwner, InferUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.Seq

/**
  * Nikolay.Tropin
  * 16-Feb-18
  */
object ImplicitParametersAnnotator extends AnnotatorPart[ImplicitParametersOwner] {

  override def annotate(element: ImplicitParametersOwner, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    element.findImplicitParameters.foreach { params =>
      UsageTracker.registerUsedElementsAndImports(element, params, checkWrite = false)

      val notFoundArgHighlightingEnabled =
        ScalaProjectSettings.getInstance(element.getProject).isShowNotFoundImplicitArguments

      if (typeAware && notFoundArgHighlightingEnabled)
        highlightNotFound(element, params, holder)
    }
  }

  private def highlightNotFound(element: ImplicitParametersOwner, parameters: Seq[ScalaResolveResult], holder: AnnotationHolder): Unit = {
    parameters.filter(_.name == InferUtil.notFoundParameterName) match {
      case Seq() =>
      case params =>
        val types = params
          .map(_.implicitSearchState.map(_.tp.presentableText).getOrElse("unknown type"))

        val annotation = holder.createErrorAnnotation(lastLineRange(element), message(types))

        //make annotation invisible in editor in favor of inlay hint
        adjustTextAttributesOf(annotation)
    }
  }

  //to avoid error stripes for several lines
  private def lastLineRange(element: PsiElement): TextRange = {
    val range = element.getTextRange
    val text = element.getText
    val lastLineBreak = text.lastIndexOf('\n')

    if (lastLineBreak >= 0) range.intersection(range.shiftRight(lastLineBreak + 1))
    else range
  }

  private def adjustTextAttributesOf(annotation: Annotation): Unit = {
    val errorStripeColor = annotation.getTextAttributes.getDefaultAttributes.getErrorStripeColor
    val attributes = new TextAttributes()
    attributes.setEffectType(null)
    attributes.setErrorStripeColor(errorStripeColor)
    annotation.setEnforcedTextAttributes(attributes)
  }

  def message(types: Seq[String]): String =
    types.mkString("No implicit arguments of type: ", ", ", "")
}
