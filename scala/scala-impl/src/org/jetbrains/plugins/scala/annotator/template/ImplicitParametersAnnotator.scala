package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.probableArgumentsFor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.Seq

/**
  * Nikolay.Tropin
  * 16-Feb-18
  */
object ImplicitParametersAnnotator extends AnnotatorPart[ImplicitArgumentsOwner] {

  override def annotate(element: ImplicitArgumentsOwner, typeAware: Boolean = true)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    element.findImplicitArguments.foreach { params =>
      UsageTracker.registerUsedElementsAndImports(element, params, checkWrite = false)

      val showImplictErrors = {
        val settings = ScalaProjectSettings.getInstance(element.getProject)
        settings.isShowNotFoundImplicitArguments || settings.isShowAmbiguousImplicitArguments
      }

      if (typeAware && showImplictErrors)
        highlightNotFound(element, params)
    }
  }

  private def highlightNotFound(element: ImplicitArgumentsOwner, parameters: Seq[ScalaResolveResult])
                               (implicit holder: ScalaAnnotationHolder): Unit = {
    val settings = ScalaProjectSettings.getInstance(element.getProject)

    parameters.filter(it =>
      it.isImplicitParameterProblem &&
        (if (probableArgumentsFor(it).size > 1) settings.isShowAmbiguousImplicitArguments
        else settings.isShowNotFoundImplicitArguments)) match {

      case Seq() =>
      case params =>
        val presentableTypes = params
          .map(_.implicitSearchState.map(_.presentableTypeText).getOrElse("unknown type"))

        val annotation = holder.createErrorAnnotation(lastLineRange(element), message(presentableTypes))

        //SearchImplicitQuickFix(params, element).foreach(annotation.registerFix)

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

  private def adjustTextAttributesOf(annotation: ScalaAnnotation): Unit = {
    val errorStripeColor = annotation.getTextAttributes.getDefaultAttributes.getErrorStripeColor
    val attributes = new TextAttributes()
    attributes.setEffectType(null)
    attributes.setErrorStripeColor(errorStripeColor)
    annotation.setEnforcedTextAttributes(attributes)
  }

  def message(types: Seq[String]): String =
    types.mkString("No implicit arguments of type: ", ", ", "")
}