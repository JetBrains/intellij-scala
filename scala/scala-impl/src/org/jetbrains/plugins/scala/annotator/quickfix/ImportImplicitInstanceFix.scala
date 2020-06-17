package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.intention.ImplicitToImport
import org.jetbrains.plugins.scala.annotator.intention.PopupPosition
import org.jetbrains.plugins.scala.annotator.intention.ScalaAddImportAction
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportElementFix
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.extensions.SeqExt
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.GlobalImplicitInstance
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.Seq

final class ImportImplicitInstanceFix private (found: Array[FoundImplicit],
                                               owner: ImplicitArgumentsOwner,
                                               popupPosition: PopupPosition)
  extends ScalaImportElementFix(owner) {

  override val elements: Seq[ImplicitToImport] = found.map(ImplicitToImport)

  override def shouldShowHint(): Boolean = false

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] = {
    val title = ScalaBundle.message("implicit.instance.to.import")
    ScalaAddImportAction.importImplicits(
      editor, elements, owner, title, popupPosition
    )
  }

  override def isAddUnambiguous: Boolean = false

  override def getText: String = ScalaBundle.message("import.implicit.instance")

  override def getFamilyName: String = getText
}

case class FoundImplicit(instance: GlobalImplicitInstance, path: Seq[ScalaResolveResult], scType: ScType)

object ImportImplicitInstanceFix {
  private case class TypeToSearch(path: Seq[ScalaResolveResult], scType: ScType)

  def apply(notFoundImplicitParams: Seq[ScalaResolveResult],
            owner: ImplicitArgumentsOwner,
            popupPosition: PopupPosition = PopupPosition.best): Option[ImportImplicitInstanceFix] = {

    val typesToSearch = notFoundImplicitParams.flatMap(withProbableArguments(Nil, _))
    val allInstances = typesToSearch.flatMap {
      case TypeToSearch(path, scType) => findCompatibleInstances(scType, owner).map(FoundImplicit(_, path, scType))
    }
    val alreadyImported =
      ImplicitCollector.visibleImplicits(owner).flatMap(GlobalImplicitInstance.from)

    val instances =
      allInstances
        .distinctBy(_.instance)
        .filterNot(x => alreadyImported.contains(x.instance))

    if (instances.nonEmpty)
      Some(new ImportImplicitInstanceFix(instances.toArray, owner, popupPosition))
    else None
  }

  private def withProbableArguments(prefix: Seq[ScalaResolveResult],
                                    parameter: ScalaResolveResult,
                                    visited: Set[PsiNamedElement] = Set.empty): Seq[TypeToSearch] = {
    if (visited(parameter.element))
      return Seq.empty

    val forParameter = implicitTypeToSearch(parameter).map(TypeToSearch(prefix :+ parameter, _))

    import ImplicitCollector._
    val forProbableArgs = probableArgumentsFor(parameter).flatMap {
      case (arg, ImplicitParameterNotFoundResult) =>
        arg.implicitParameters.flatMap(withProbableArguments(prefix :+ arg, _, visited + parameter.element))
      case _ => Seq.empty
    }

    forParameter ++: forProbableArgs
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