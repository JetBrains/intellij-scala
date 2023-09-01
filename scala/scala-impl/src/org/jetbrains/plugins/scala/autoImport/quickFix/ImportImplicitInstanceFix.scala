package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.autoImport.GlobalImplicitInstance
import org.jetbrains.plugins.scala.autoImport.GlobalMember.findGlobalMembers
import org.jetbrains.plugins.scala.autoImport.quickFix.ImportImplicitInstanceFix.implicitsToImport
import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportElementFix.isExcluded
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ImplicitInstanceIndex, ScGivenIndex}
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.withStableInheritorsNames
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.{AnyFqn, AnyRefFqn, JavaLangObjectFqn}

final class ImportImplicitInstanceFix private (notFoundImplicitParams: () => Seq[ScalaResolveResult],
                                               owner: ImplicitArgumentsOwner,
                                               popupPosition: PopupPosition)
  extends ScalaImportElementFix[ImplicitToImport](owner) {

  override protected def findElementsToImport(): Seq[ImplicitToImport] = implicitsToImport(notFoundImplicitParams(), owner)

  override def shouldShowHint(): Boolean =
    super.shouldShowHint() && ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_IMPLICITS

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction.importImplicits(editor, elements, owner, popupPosition)

  override def isAddUnambiguous: Boolean = false

  override def getText: String = {
    if (elements.size == 1)
      ScalaBundle.message("import.with", elements.head.qualifiedName)
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

  def apply(notFoundImplicitParams: () => Seq[ScalaResolveResult],
            owner: ImplicitArgumentsOwner,
            popupPosition: PopupPosition = PopupPosition.best): ImportImplicitInstanceFix =
    new ImportImplicitInstanceFix(notFoundImplicitParams, owner, popupPosition)

  private[quickFix] def implicitsToImport(notFoundImplicitParams: Seq[ScalaResolveResult], owner: ImplicitArgumentsOwner): Seq[ImplicitToImport] = {
    val typesToSearch = notFoundImplicitParams.flatMap(withProbableArguments(Nil, _))
    val allInstances = typesToSearch.flatMap {
      case TypeToSearch(path, scType) => findCompatibleInstances(scType, owner).map(FoundImplicit(_, path, scType))
    }
    val alreadyImported =
      ImplicitCollector.visibleImplicits(owner).flatMap(GlobalImplicitInstance.from)

    allInstances
      .distinctBy(_.instance)
      .filterNot(x => alreadyImported.contains(x.instance) || isExcluded(x.instance.qualifiedName, owner.getProject))
      .sortBy {
        case FoundImplicit(instance, path, scType) =>
          (path.size, typesToSearch.indexWhere(_.scType == scType), instance.qualifiedName)
      }
      .map(ImplicitToImport)
  }

  private def withProbableArguments(prefix: Seq[ScalaResolveResult],
                                    parameter: ScalaResolveResult,
                                    visited: Set[PsiNamedElement] = Set.empty): Seq[TypeToSearch] = {
    if (visited(parameter.element) || visited.size > 2)
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

    val types = `type` match {
      case ScCompoundType(components, _, _) => components.toSet
      case _ => Set(`type`)
    }

    for {
      tpe <- types
      clazz <- tpe.extractClass.toSet[PsiClass]

      qualifiedName <- withStableInheritorsNames(clazz)
      if !isRootClass(qualifiedName)

      candidateMember <-
        ImplicitInstanceIndex.forClassFqn(qualifiedName, scope)(place.getProject) ++
          ScGivenIndex.forClassFqn(qualifiedName, scope)(place.getProject)

      global <- findGlobalMembers(candidateMember, scope)(GlobalImplicitInstance(_, _, _))
      if checkCompatible(global, collector)
    } yield global
  }

  private[this] def isRootClass(qualifiedName: String) = qualifiedName match {
    case AnyRefFqn | AnyFqn | JavaLangObjectFqn => true
    case _ => false
  }

  private def checkCompatible(global: GlobalImplicitInstance, collector: ImplicitCollector): Boolean = {
    val srr = global.toScalaResolveResult
    collector.checkCompatible(srr, withLocalTypeInference = false)
      .orElse(collector.checkCompatible(srr, withLocalTypeInference = true))
      .exists(ImplicitCollector.isValidImplicitResult)
  }

  private def implicitTypeToSearch(parameter: ScalaResolveResult): Option[ScType] =
    parameter.implicitSearchState.map(_.tp)

  private def isScalaFunction1Type(scType: ScType): Boolean =
    scType match {
      case FunctionType(_, _) => true
      case _ => false
    }
}
