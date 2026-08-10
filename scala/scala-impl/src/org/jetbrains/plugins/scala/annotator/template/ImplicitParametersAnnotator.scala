package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColorsScheme}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.{AnnotatorPart, ScalaAnnotationHolder}
import org.jetbrains.plugins.scala.annotator.hints.onlyErrorStripeAttributes
import org.jetbrains.plugins.scala.autoImport.quickFix.ImportImplicitInstanceFix
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.probableArgumentsFor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

object ImplicitParametersAnnotator extends AnnotatorPart[ImplicitArgumentsOwner] {

  override def annotate(element: ImplicitArgumentsOwner, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    element.findImplicitArguments.foreach { argsByClause =>
      val showImplictErrors = {
        val settings = ScalaProjectSettings.getInstance(element.getProject)
        settings.isShowNotFoundImplicitArguments || settings.isShowAmbiguousImplicitArguments
      }

      if (typeAware && showImplictErrors)
        highlightNotFound(element, argsByClause.args)
    }
  }

  private def highlightNotFound(element: ImplicitArgumentsOwner, args: Seq[ScalaResolveResult])
                               (implicit holder: ScalaAnnotationHolder): Unit = {
    val settings = ScalaProjectSettings.getInstance(element.getProject)

    args.filter(hasProblemToHighlight(_, settings)) match {
      case Seq() =>
      case params =>
        // In Scala 3 no search is attempted for underspecified expected types (SCL-23860,
        // see ImplicitCollector.tooUnspecificToSearch); report those with the compiler's
        // wording and without the import fix — importing an instance cannot help there.
        val (tooUnspecific, searched) = params.partition(ImplicitCollector.isTooUnspecificToSearch)

        def presentableTypes(params: Seq[ScalaResolveResult]): Seq[String] =
          params.map(ImplicitCollector.expectedTypeText(_).getOrElse(ScalaBundle.message("unknown.type")))

        // TODO Can we detect a "current" color scheme in a "current" editor somehow?
        implicit val scheme: EditorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme

        if (searched.nonEmpty) {
          val notFound = args.filter(arg => arg.isNotFoundImplicitParameter && !tooUnspecific.contains(arg))

          holder.newAnnotation(HighlightSeverity.ERROR, message(presentableTypes(searched)))
            .range(lastLineRange(element))
            .withFix(ImportImplicitInstanceFix(() => notFound, element))
            .enforcedTextAttributes(onlyErrorStripeAttributes)  //make annotation invisible in editor in favor of inlay hint
            .create()
        }

        if (tooUnspecific.nonEmpty) {
          holder.newAnnotation(HighlightSeverity.ERROR, notSpecificEnoughMessage(presentableTypes(tooUnspecific)))
            .range(lastLineRange(element))
            .enforcedTextAttributes(onlyErrorStripeAttributes)  //make annotation invisible in editor in favor of inlay hint
            .create()
        }
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

  def notSpecificEnoughMessage(types: Seq[String]): String =
    ScalaBundle.message("no.implicit.search.was.attempted.type.not.specific.enough", types.mkString(", "))
}
