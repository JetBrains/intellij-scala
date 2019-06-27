package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.{JBPopupFactory, PopupStep}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import com.intellij.util.ObjectUtils
import javax.swing.Icon
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

        val notFoundTypes = params.flatMap(withProbableArguments(_)).flatMap(implicitTypeToSearch)
        if (notFoundTypes.nonEmpty) {
          annotation.registerFix(new SearchImplicitQuickFix(notFoundTypes, element))
        }

        //make annotation invisible in editor in favor of inlay hint
        adjustTextAttributesOf(annotation)
    }
  }

  private def withProbableArguments(parameter: ScalaResolveResult,
                                    visited: Set[PsiNamedElement] = Set.empty): Seq[ScalaResolveResult] = {
    if (visited(parameter.element))
      return Seq.empty

    val arguments = ImplicitCollector.probableArgumentsFor(parameter).flatMap {
      case (arg, ImplicitParameterNotFoundResult) => arg.implicitParameters.flatMap(withProbableArguments(_, visited + parameter.element))
      case _                                      => Seq.empty
    }

    parameter +: arguments
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

  private class SearchImplicitQuickFix(typesToSearch: Seq[ScType], place: ImplicitArgumentsOwner) extends IntentionAction {
    def getText: String = {
      val typeOrEllipsis = typesToSearch match {
        case Seq(tp) => tp.presentableText(place)
        case _ => "..."
      }
      s"Search implicit instances for $typeOrEllipsis"
    }

    def getFamilyName: String = "Search implicit instances"

    def isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean = true

    def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
      typesToSearch match {
        case Seq(tp) => searchAndSuggestImport(tp, editor)
        case _       => chooseType(editor)
      }
    }

    private def chooseType(editor: Editor): Unit = {
      val popup = new BaseListPopupStep("Choose type to search", typesToSearch: _*) {
        override def getIconFor(aValue: ScType): Icon = null

        override def getTextFor(value: ScType): String =
          ObjectUtils.assertNotNull(value.presentableText(place))

        override def isAutoSelectionEnabled: Boolean = false

        override def onChosen(selectedValue: ScType, finalChoice: Boolean): PopupStep[_] = {
          searchAndSuggestImport(selectedValue, editor)
          PopupStep.FINAL_CHOICE
        }
      }
      JBPopupFactory.getInstance.createListPopup(popup)
        .showInBestPositionFor(editor)
    }

    private def searchAndSuggestImport(typeToSearch: ScType, editor: Editor): Unit = {
      val instances = GlobalImplicitInstance.compatibleInstances(typeToSearch, place.elementScope)

      if (instances.isEmpty)
        HintManager.getInstance().showInformationHint(editor, "Applicable implicits not found")
      else {
        val title = ScalaBundle.message("import.implicitInstance.chooser.title")
        ScalaAddImportAction.importImplicits(editor, instances.map(ImplicitToImport).toArray, place, title)
          .execute
      }
    }

    override def startInWriteAction(): Boolean = true
  }
}