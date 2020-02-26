package org.jetbrains.plugins.scala.annotator.template

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.{JBPopupFactory, PopupStep}
import com.intellij.psi.{PsiFile, PsiNamedElement}
import com.intellij.util.ObjectUtils
import javax.swing.Icon
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.intention.{ImplicitToImport, ScalaAddImportAction}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.ImplicitParameterNotFoundResult
import org.jetbrains.plugins.scala.lang.psi.implicits.{GlobalImplicitInstance, ImplicitCollector}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.Seq

private class SearchImplicitQuickFix(typesToSearch: Seq[ScType], place: ImplicitArgumentsOwner) extends IntentionAction {
  override def getText: String = {
    val typeOrEllipsis = typesToSearch match {
      case Seq(tp) => tp.presentableText(place)
      case _ => "..."
    }
    ScalaBundle.message("search.implicit.instances.for", typeOrEllipsis)
  }

  override def getFamilyName: String = ScalaBundle.message("family.name.search.implicit.instances")

  override def isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean = true

  override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
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

private object SearchImplicitQuickFix {
  def apply(notFoundImplicitParams: Seq[ScalaResolveResult],
            owner: ImplicitArgumentsOwner): Option[SearchImplicitQuickFix] = {

    val notFoundTypes = notFoundImplicitParams.flatMap(withProbableArguments(_)).flatMap(implicitTypeToSearch)

    if (notFoundTypes.nonEmpty)
      Some(new SearchImplicitQuickFix(notFoundTypes, owner))
    else None
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

}