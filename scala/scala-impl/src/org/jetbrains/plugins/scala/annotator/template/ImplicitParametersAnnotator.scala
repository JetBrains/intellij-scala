package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker
import org.jetbrains.plugins.scala.autoImport.quickFix.ImportImplicitInstanceFix
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.probableArgumentsFor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

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

  private def highlightNotFound(element: ImplicitArgumentsOwner, parameters: collection.Seq[ScalaResolveResult])
                               (implicit holder: ScalaAnnotationHolder): Unit = {
    val settings = ScalaProjectSettings.getInstance(element.getProject)

    parameters.filter(hasProblemToHighlight(_, settings)) match {
      case collection.Seq() =>
      case params =>
        val presentableTypes = params
          .map(_.implicitSearchState.map(_.presentableTypeText).getOrElse("unknown type"))

        val annotation = holder.createErrorAnnotation(lastLineRange(element), message(presentableTypes))

        val notFound = parameters.filter(_.isNotFoundImplicitParameter)
        val importImplicitInstanceFix = ImportImplicitInstanceFix(notFound, element)
        if (importImplicitInstanceFix.exists(_.isAvailable)) {
          importImplicitInstanceFix.foreach(annotation.registerFix)
        }

        //make annotation invisible in editor in favor of inlay hint
        adjustTextAttributesOf(annotation)
    }


  }

  private def hasProblemToHighlight(param: ScalaResolveResult, settings: ScalaProjectSettings): Boolean = {
    param.isImplicitParameterProblem &&
      (if (probableArgumentsFor(param).size > 1) settings.isShowAmbiguousImplicitArguments
      else settings.isShowNotFoundImplicitArguments)
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

  def message(types: collection.Seq[String]): String =
    types.mkString("No implicit arguments of type: ", ", ", "")
}