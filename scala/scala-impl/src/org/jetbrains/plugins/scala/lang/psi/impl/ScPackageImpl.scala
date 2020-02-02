package org.jetbrains.plugins.scala
package lang
package psi
package impl

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScPackageLike}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveState}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.04.2010
 */
final class ScPackageImpl private(val pack: PsiPackage) extends PsiPackageImpl(
  pack.getManager.asInstanceOf[PsiManagerEx],
  pack.getQualifiedName
) with ScPackage {

  import ScPackageImpl._

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    val qualifiedName = getQualifiedName
    val isInScalaContext = place.getLanguage.isKindOf(ScalaLanguage.INSTANCE)

    qualifiedName match {
      case ScalaLowerCase if isInScalaContext =>
        if (!BaseProcessor.isImplicitProcessor(processor)) {
          processScalaPackage(processor)(
            ScalaPsiManager.instance(getProject),
            findScope(processor, place)
          )
        }
      case _ =>
        if (!ResolveUtils.packageProcessDeclarations(pack, processor, state, lastParent, place)) return false
    }

    if (isInScalaContext) {
      val scope = findScope(processor, place)

      val maybeObject = qualifiedName match {
        case ScalaLowerCase => ElementScope(place.getProject, scope).getCachedObject(qualifiedName) // TODO impossible!
        case _ => findPackageObject(scope)
      }

      maybeObject.forall { obj =>
        val newState = obj.`type`().fold(
          Function.const(state),
          state.withFromType
        )

        obj.processDeclarations(processor, newState, lastParent, place)
      }
    } else {
      true
    }
  }

  @CachedInUserData(this, ScalaPsiManager.instance(getProject).TopLevelModificationTracker)
  def findPackageObject(scope: GlobalSearchScope): Option[ScObject] =
    ScalaShortNamesCacheManager.getInstance(getProject).findPackageObjectByName(getQualifiedName, scope)

  override def getParentPackage: PsiPackageImpl =
    ScalaPsiUtil.parentPackage(getQualifiedName, getProject)
      .orNull

  override def getSubPackages: Array[PsiPackage] =
    super.getSubPackages
      .map(ScPackageImpl(_))

  override def getSubPackages(scope: GlobalSearchScope): Array[PsiPackage] =
    super.getSubPackages(scope)
      .map(ScPackageImpl(_))

  override def isValid: Boolean = true

  override def parentScalaPackage: Option[ScPackageLike] = getParentPackage match {
    case p: ScPackageLike => Some(p)
    case _ => None
  }
}

object ScPackageImpl {

  def apply(psiPackage: PsiPackage): ScPackageImpl = psiPackage match {
    case impl: ScPackageImpl => impl
    case null => null
    case _ => new ScPackageImpl(psiPackage)
  }

  def findPackage(project: Project, pName: String): ScPackageImpl =
    ScPackageImpl(ScalaPsiManager.instance(project).getCachedPackage(pName).orNull)

  /**
   * Process synthetic classes for scala._ package
   */
  def processScalaPackage(processor: PsiScopeProcessor,
                          state: ResolveState = ScalaResolveState.empty)
                         (implicit manager: ScalaPsiManager,
                          scope: GlobalSearchScope): Boolean = {
    val namesSet = manager.getScalaPackageClassNames

    val syntheticClasses = SyntheticClasses.get(manager.project)
    for {
      syntheticElement <- syntheticClasses.getAll ++
        syntheticClasses.syntheticObjects.valuesIterator
      // Assume that is the scala package contained a class with the same names as the synthetic object, then it must also contain the object.

      // Does the "scala" package already contain a class named `className`?
      // SCL-2913
      if !namesSet(syntheticElement.name)
    } {
      ProgressManager.checkCanceled()
      if (!processor.execute(syntheticElement, state)) return false
    }

    true
  }

  private def findScope(processor: PsiScopeProcessor,
                place: PsiElement) = processor match {
    case processor: ResolveProcessor => processor.getResolveScope
    case _ => place.resolveScope
  }
}