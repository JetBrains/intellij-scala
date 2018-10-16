package org.jetbrains.plugins.scala
package lang
package psi
package impl

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.caches.{CachesUtil, ScalaShortNamesCacheManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScPackageLike}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.04.2010
 */
class ScPackageImpl private (val pack: PsiPackage) extends PsiPackageImpl(pack.getManager.asInstanceOf[PsiManagerEx],
        pack.getQualifiedName) with ScPackage {

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    if (place.getLanguage.isKindOf(ScalaLanguage.INSTANCE) && pack.getQualifiedName == "scala") {
      if (!BaseProcessor.isImplicitProcessor(processor)) {
        val scope = processor match {
          case r: ResolveProcessor => r.getResolveScope
          case _ => place.resolveScope
        }
        val namesSet = ScalaShortNamesCacheManager.getInstance(getProject).getClassNames(pack, scope)

        //Process synthetic classes for scala._ package
        /**
         * Does the "scala" package already contain a class named `className`?
         *
         * [[http://youtrack.jetbrains.net/issue/SCL-2913]]
         */
        def alreadyContains(className: String) = namesSet.contains(className)

        for (synth <- SyntheticClasses.get(getProject).getAll) {
          if (!alreadyContains(synth.name)) processor.execute(synth, ResolveState.initial)
        }
        for (synthObj <- SyntheticClasses.get(getProject).syntheticObjects) {

          // Assume that is the scala package contained a class with the same names as the synthetic object,
          // then it must also contain the object.
          if (!alreadyContains(synthObj.name)) processor.execute(synthObj, ResolveState.initial)
        }
      }
    } else {
      if (!ResolveUtils.packageProcessDeclarations(pack, processor, state, lastParent, place)) return false
    }

    //for Scala
    if (place.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) {
      val scope = processor match {
        case r: ResolveProcessor => r.getResolveScope
        case _ => place.resolveScope
      }

      val maybeObject = getQualifiedName match {
        case fqn@"scala" =>
          ElementScope(place.getProject, scope).getCachedObject(fqn)
        case _ =>
          findPackageObject(scope).collect {
            case scalaObject: ScObject => scalaObject
          }
      }

      maybeObject.forall { obj =>
        val newState = obj.`type`().toOption.fold(state) {
          state.put(BaseProcessor.FROM_TYPE_KEY, _)
        }

        obj.processDeclarations(processor, newState, lastParent, place)
      }
    } else true
  }

  def findPackageObject(scope: GlobalSearchScope): Option[ScTypeDefinition] = {
    val manager = ScalaShortNamesCacheManager.getInstance(getProject)

    var tuple = pack.getUserData(CachesUtil.PACKAGE_OBJECT_KEY)
    val count = ScalaPsiManager.instance(getProject).getModificationCount
    if (tuple == null || tuple._2.longValue != count) {
      val clazz = manager.getPackageObjectByName(getQualifiedName, scope)
      tuple = (clazz, java.lang.Long.valueOf(count)) // TODO is it safe to cache this ignoring `scope`?
      pack.putUserData(CachesUtil.PACKAGE_OBJECT_KEY, tuple)
    }
    Option(tuple._1)
  }

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
}