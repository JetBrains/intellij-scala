package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.util.PsiTreeUtil
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

        val lastLeaf = PsiTreeUtil.getDeepestLast(element) //to avoid error stripes for several lines

        val annotation = holder.createErrorAnnotation(lastLeaf, message(types))

        //make annotation invisible in editor in favor of inlay hint
        adjustTextAttributesOf(annotation)
    }
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
