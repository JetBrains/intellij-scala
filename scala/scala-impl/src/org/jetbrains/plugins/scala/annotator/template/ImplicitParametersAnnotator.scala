package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColorsScheme}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.hints.onlyErrorStripeAttributes
import org.jetbrains.plugins.scala.autoImport.quickFix.ImportImplicitInstanceFix
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.probableArgumentsFor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

object ImplicitParametersAnnotator extends AnnotatorPart[ImplicitArgumentsOwner] {

  override def annotate(element: ImplicitArgumentsOwner, typeAware: Boolean = true)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    element.findImplicitArguments.foreach { params =>
      val showImplictErrors = {
        val settings = ScalaProjectSettings.getInstance(element.getProject)
        settings.isShowNotFoundImplicitArguments || settings.isShowAmbiguousImplicitArguments
      }

      if (typeAware && showImplictErrors)
        highlightNotFound(element, params.toSeq)
    }
  }

  private def highlightNotFound(element: ImplicitArgumentsOwner, parameters: Seq[ScalaResolveResult])
                               (implicit holder: ScalaAnnotationHolder): Unit = {
    val settings = ScalaProjectSettings.getInstance(element.getProject)

    parameters.filter(hasProblemToHighlight(_, settings)) match {
      case Seq() =>
      case params =>
        val presentableTypes = params
          .map(_.implicitSearchState.map(_.presentableTypeText).getOrElse(ScalaBundle.message("unknown.type")))
        val notFound = parameters.filter(_.isNotFoundImplicitParameter)

        // TODO Can we detect a "current" color scheme in a "current" editor somehow?
        implicit val scheme: EditorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme

        holder.newAnnotation(HighlightSeverity.ERROR, message(presentableTypes))
          .range(lastLineRange(element))
          .withFix(ImportImplicitInstanceFix(() => notFound, element))
          .enforcedTextAttributes(onlyErrorStripeAttributes)  //make annotation invisible in editor in favor of inlay hint
          .create()
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

  def message(types: Seq[String]): String =
    ScalaBundle.message("no.implicit.arguments.of.type", types.mkString(", "))
}
