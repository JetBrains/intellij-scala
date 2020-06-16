package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.intention.ElementToImport
import org.jetbrains.plugins.scala.annotator.intention.ImplicitToImport
import org.jetbrains.plugins.scala.annotator.intention.PopupPosition
import org.jetbrains.plugins.scala.annotator.intention.ScalaAddImportAction
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportElementFix
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.GlobalImplicitInstance
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.Seq

final class ImportImplicitInstanceFix(instances: Array[ElementToImport],
                                      owner: ImplicitArgumentsOwner,
                                      popupPosition: PopupPosition) extends ScalaImportElementFix(instances, owner) {

  override def shouldShowHint(): Boolean = false

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_] =
    ScalaAddImportAction.importImplicits(editor, elements, owner, ScalaBundle.message("implicit.instance.to.import"), popupPosition)

  override def isAddUnambiguous: Boolean = false

  override def getText: String = ScalaBundle.message("import.implicit.instance")

  override def getFamilyName: String = getText
}

object ImportImplicitInstanceFix {
  private case class TypeToSearch(path: Seq[ScalaResolveResult], scType: Option[ScType])

  def apply(notFoundImplicitParams: Seq[ScalaResolveResult],
            owner: ImplicitArgumentsOwner,
            popupPosition: PopupPosition = PopupPosition.best): Option[ImportImplicitInstanceFix] = {

    val typesToSearch = notFoundImplicitParams.flatMap(withProbableArguments(Nil, _)).flatMap(_.scType)
    val allInstances = typesToSearch.flatMap(findCompatibleInstances(_, owner)).toSet
    val alreadyImported = ImplicitCollector.visibleImplicits(owner).flatMap(GlobalImplicitInstance.from)

    val instances = allInstances -- alreadyImported

    if (instances.nonEmpty)
      Some(new ImportImplicitInstanceFix(instances.map(ImplicitToImport).toArray, owner, popupPosition))
    else None
  }

  private def withProbableArguments(prefix: Seq[ScalaResolveResult],
                                    parameter: ScalaResolveResult,
                                    visited: Set[PsiNamedElement] = Set.empty): Seq[TypeToSearch] = {
    if (visited(parameter.element))
      return Seq.empty

    val forParameter = TypeToSearch(prefix :+ parameter, implicitTypeToSearch(parameter))

    import ImplicitCollector._
    val forProbableArgs = probableArgumentsFor(parameter).flatMap {
      case (arg, ImplicitParameterNotFoundResult) =>
        arg.implicitParameters.flatMap(withProbableArguments(prefix :+ arg, _, visited + parameter.element))
      case _ => Seq.empty
    }

    forParameter +: forProbableArgs
  }

  private def findCompatibleInstances(typeToSearch: ScType, owner: ImplicitArgumentsOwner): Set[GlobalImplicitInstance] = {
    val allInstances =
      GlobalImplicitInstance.compatibleInstances(typeToSearch, owner.resolveScope, owner)

    val availableByType =
      ImplicitCollector.implicitsFromType(owner, typeToSearch).flatMap(GlobalImplicitInstance.from)

    allInstances -- availableByType
  }

  private def implicitTypeToSearch(parameter: ScalaResolveResult): Option[ScType] =
    parameter.implicitSearchState.map(_.tp)

}