package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.autoImport.GlobalImplicitInstance
import org.jetbrains.plugins.scala.autoImport.GlobalMember.findGlobalMembers
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, TraversableExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.isInExcludedPackage
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.TypeDoesntConformResult
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitInstanceIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.withStableInheritorsNames
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, WrongTypeParameterInferred}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.{AnyFqn, AnyRefFqn, JavaObjectFqn}

final class ImportImplicitInstanceFix private (found: collection.Seq[FoundImplicit],
                                               owner: ImplicitArgumentsOwner,
                                               popupPosition: PopupPosition)
  extends ScalaImportElementFix(owner) {

  override val elements: collection.Seq[ImplicitToImport] = found.map(ImplicitToImport)

  override def shouldShowHint(): Boolean =
    super.shouldShowHint() && ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_IMPLICITS

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction.importImplicits(editor, elements, owner, popupPosition)

  override def isAddUnambiguous: Boolean = false

  override def getText: String = {
    if (found.size == 1)
      ScalaBundle.message("import.with", found.head.instance.qualifiedName)
    else
      ScalaBundle.message("import.implicit")
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

  def apply(notFoundImplicitParams: collection.Seq[ScalaResolveResult],
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
        .filterNot(x => alreadyImported.contains(x.instance) || isInExcludedPackage(x.instance.pathToOwner, owner.getProject))
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
                                    visited: Set[PsiNamedElement] = Set.empty): collection.Seq[TypeToSearch] = {
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
      compatibleInstances(typeToSearch, owner.resolveScope, owner)

    val availableByType =
      ImplicitCollector.implicitsFromType(owner, typeToSearch).flatMap(GlobalImplicitInstance.from)

    allInstances -- availableByType
  }

  private def compatibleInstances(`type`: ScType,
                                  scope: GlobalSearchScope,
                                  place: ImplicitArgumentsOwner): Set[GlobalImplicitInstance] = {
    val collector = new ImplicitCollector(place, `type`, `type`, None, false, fullInfo = true)
    for {
      clazz <- `type`.extractClass.toSet[PsiClass]

      qualifiedName <- withStableInheritorsNames(clazz, scope)
      if !isRootClass(qualifiedName)

      candidateMember <- ImplicitInstanceIndex.forClassFqn(qualifiedName, scope)(place.getProject)

      global <- findGlobalMembers(candidateMember, scope)(GlobalImplicitInstance(_, _, _))
      if checkCompatible(global, collector)
    } yield global
  }

  private[this] def isRootClass(qualifiedName: String) = qualifiedName match {
    case AnyRefFqn | AnyFqn | JavaObjectFqn => true
    case _ => false
  }

  private def checkCompatible(global: GlobalImplicitInstance, collector: ImplicitCollector): Boolean = {
    val srr = global.toScalaResolveResult
    collector.checkCompatible(srr, withLocalTypeInference = false)
      .orElse(collector.checkCompatible(srr, withLocalTypeInference = true))
      .exists(isCompatible)
  }

  private def isCompatible(srr: ScalaResolveResult): Boolean = {
    !srr.problems.contains(WrongTypeParameterInferred) && srr.implicitReason != TypeDoesntConformResult
  }

  private def implicitTypeToSearch(parameter: ScalaResolveResult): Option[ScType] =
    parameter.implicitSearchState.map(_.tp)

  private def isScalaFunction1Type(scType: ScType): Boolean =
    scType match {
      case FunctionType(_, _) => true
      case _ => false
    }
}