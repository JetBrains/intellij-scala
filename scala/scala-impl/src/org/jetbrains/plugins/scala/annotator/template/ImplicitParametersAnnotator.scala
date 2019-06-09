package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.annotator.intention.{ImplicitToImport, ScalaAddImportAction}
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.ImplicitParameterNotFoundResult
import org.jetbrains.plugins.scala.lang.psi.implicits.{GlobalImplicitInstance, ImplicitCollector}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.Seq

/**
  * Nikolay.Tropin
  * 16-Feb-18
  */
object ImplicitParametersAnnotator extends AnnotatorPart[ImplicitArgumentsOwner] {

  override def annotate(element: ImplicitArgumentsOwner, typeAware: Boolean = true)
                       (implicit holder: AnnotationHolder): Unit = {
    element.findImplicitArguments.foreach { params =>
      UsageTracker.registerUsedElementsAndImports(element, params, checkWrite = false)

      val notFoundArgHighlightingEnabled =
        ScalaProjectSettings.getInstance(element.getProject).isShowNotFoundImplicitArguments

      if (typeAware && notFoundArgHighlightingEnabled)
        highlightNotFound(element, params)
    }
  }

  private def highlightNotFound(element: ImplicitArgumentsOwner, parameters: Seq[ScalaResolveResult])
                               (implicit holder: AnnotationHolder): Unit = {
    //todo: cover ambiguous implicit case (right now it is not always correct)
    parameters.filter(_.isNotFoundImplicitParameter) match {
      case Seq() =>
      case params =>
        val presentableTypes = params
          .map(_.implicitSearchState.map(_.presentableTypeText).getOrElse("unknown type"))

        val annotation = holder.createErrorAnnotation(lastLineRange(element), message(presentableTypes))

        val notFoundType = params.flatMap(withoutApplicableInstances).headOption
        notFoundType.foreach(tp => annotation.registerFix(new SearchImplicitQuickFix(tp, element)))

        //make annotation invisible in editor in favor of inlay hint
        adjustTextAttributesOf(annotation)
    }
  }

  private def withoutApplicableInstances(parameter: ScalaResolveResult): Seq[ScType] = {
    ImplicitCollector.probableArgumentsFor(parameter) match {
      case Seq() => implicitTypeToSearch(parameter).toSeq
      case args  =>
        args.flatMap {
          case (arg, ImplicitParameterNotFoundResult) => arg.implicitParameters.flatMap(withoutApplicableInstances)
          case _                                      => Seq.empty
        }
    }
  }

  private def implicitTypeToSearch(parameter: ScalaResolveResult): Option[ScType] =
    parameter.implicitSearchState.map(_.tp)

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

  private class SearchImplicitQuickFix(notFoundType: ScType, element: ImplicitArgumentsOwner) extends IntentionAction {
    def getText: String = s"Search implicit instances for ${notFoundType.presentableText(element)}"

    def getFamilyName: String = getText

    def isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean = true

    def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
      val instances = GlobalImplicitInstance.compatibleInstances(notFoundType, element.elementScope)

      if (instances.isEmpty)
        HintManager.getInstance().showInformationHint(editor, "Applicable implicits not found")
      else {
        val title = ScalaBundle.message("import.implicitInstance.chooser.title")
        ScalaAddImportAction.importImplicits(editor, instances.map(ImplicitToImport).toArray, element, title)
          .execute
      }
    }

    override def startInWriteAction(): Boolean = true
  }
}