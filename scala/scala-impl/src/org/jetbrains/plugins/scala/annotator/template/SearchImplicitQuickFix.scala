package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.{JBPopupFactory, PopupStep}
import com.intellij.psi.{PsiNamedElement, PsiFile}
import com.intellij.util.ObjectUtils
import javax.swing.Icon
import javax.swing.JLabel
import org.jetbrains.plugins.scala.annotator.intention.PopupPosition
import org.jetbrains.plugins.scala.annotator.intention.{ImplicitToImport, ScalaAddImportAction}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.{GlobalImplicitInstance, ImplicitCollector}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.Seq

private class SearchImplicitQuickFix(typesToSearch: Seq[ScType],
                                     place: ImplicitArgumentsOwner,
                                     popupPosition: PopupPosition) extends IntentionAction {
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
    val listPopup = JBPopupFactory.getInstance.createListPopup(popup)
    popupPosition.showPopup(listPopup, editor)
  }

  private def searchAndSuggestImport(typeToSearch: ScType, editor: Editor): Unit = {
    val allInstances =
      GlobalImplicitInstance.compatibleInstances(typeToSearch, place.resolveScope, place)

    val alreadyAvailable =
      ImplicitCollector.visibleImplicits(place) ++ ImplicitCollector.implicitsFromType(place, typeToSearch)

    val instances = allInstances -- alreadyAvailable.flatMap(GlobalImplicitInstance.from)

    if (instances.isEmpty) {
      val popup = JBPopupFactory.getInstance().createMessage("Applicable implicits not found")
      popupPosition.showPopup(popup, editor)
    } else {
      val title = ScalaBundle.message("import.implicitInstance.chooser.title")
      ScalaAddImportAction.importImplicits(editor, instances.map(ImplicitToImport).toArray, place, title, popupPosition)
        .execute()
    }
  }

  override def startInWriteAction(): Boolean = true
}

object SearchImplicitQuickFix {
  def apply(notFoundImplicitParams: Seq[ScalaResolveResult],
            owner: ImplicitArgumentsOwner,
            searchProbableArgs: Boolean,
            popupPosition: PopupPosition = PopupPosition.best): Option[IntentionAction] = {

    val allArguments =
      if (searchProbableArgs) notFoundImplicitParams.flatMap(withProbableArguments(_))
      else notFoundImplicitParams

    val notFoundTypes = allArguments.flatMap(implicitTypeToSearch)

    if (notFoundTypes.nonEmpty)
      Some(new SearchImplicitQuickFix(notFoundTypes.distinct, owner, popupPosition))
    else None
  }

  private def withProbableArguments(parameter: ScalaResolveResult,
                                    visited: Set[PsiNamedElement] = Set.empty): Seq[ScalaResolveResult] = {
    if (visited(parameter.element))
      return Seq.empty

    import ImplicitCollector._
    val arguments = probableArgumentsFor(parameter).flatMap {
      case (arg, ImplicitParameterNotFoundResult) => arg.implicitParameters.flatMap(withProbableArguments(_, visited + parameter.element))
      case _ => Seq.empty
    }

    parameter +: arguments
  }

  private def implicitTypeToSearch(parameter: ScalaResolveResult): Option[ScType] =
    parameter.implicitSearchState.map(_.tp)

}