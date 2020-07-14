package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.completion.JavaCompletionUtil.isInExcludedPackage
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.intention.{ImplicitToImport, PopupPosition, ScalaAddImportAction, ScalaImportElementFix}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, TraversableExt}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.{GlobalImplicitInstance, ImplicitCollector}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import scala.collection.Seq

final class ImportImplicitInstanceFix private (found: Seq[FoundImplicit],
                                               owner: ImplicitArgumentsOwner,
                                               popupPosition: PopupPosition)
  extends ScalaImportElementFix(owner) {

  override val elements: Seq[ImplicitToImport] = found.map(ImplicitToImport)

  override def shouldShowHint(): Boolean =
    super.shouldShowHint() && ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_IMPLICITS

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction.importImplicits(editor, elements, owner, popupPosition)

  override def isAddUnambiguous: Boolean = false

  override def getTextInner: String = elements match {
    case Seq(element) => ScalaBundle.message("import.with", element.presentationBody)
    case _            => ScalaBundle.message("import.implicit")
  }

  override def getFamilyName: String =
    ScalaBundle.message("import.implicit")

  override protected def getHintRange: (Int, Int) = {
    val endOffset = owner.endOffset
    (endOffset, endOffset)
  }
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
        .filterNot(x => alreadyImported.contains(x.instance) || isInExcludedPackage(x.instance.containingObject, false))
        .sortBy {
          case FoundImplicit(instance, path, scType) =>
            (path.size, typesToSearch.indexWhere(_.scType == scType), instance.qualifiedName)
        }

    if (instances.nonEmpty)
      Some(new ImportImplicitInstanceFix(instances, owner, popupPosition))
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
    //this happens for view bounds, let's skip this for now
    if (isScalaFunction1Type(typeToSearch))
      return Set.empty

    val allInstances =
      GlobalImplicitInstance.compatibleInstances(typeToSearch, owner.resolveScope, owner)

    val availableByType =
      ImplicitCollector.implicitsFromType(owner, typeToSearch).flatMap(GlobalImplicitInstance.from)

    allInstances -- availableByType
  }

  private def implicitTypeToSearch(parameter: ScalaResolveResult): Option[ScType] =
    parameter.implicitSearchState.map(_.tp)

  private def isScalaFunction1Type(scType: ScType): Boolean =
    scType match {
      case FunctionType(_, _) => true
      case _ => false
    }
}