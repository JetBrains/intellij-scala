package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.psi.impl.file.PsiPackageImpl
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi._
import impl.PsiManagerEx
import scope.PsiScopeProcessor.Event
import scope.PsiScopeProcessor
import java.lang.String
import com.intellij.openapi.util.Key
import collection.Iterator
import statements.ScFunctionImpl
import toplevel.synthetic.{ScSyntheticPackage, SyntheticClasses}
import org.jetbrains.plugins.scala.caches.{CachesUtil, ScalaCachesManager}
import util.{PsiModificationTracker, CachedValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject, ScClass}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.04.2010
 */

class ScPackageImpl(pack: PsiPackage) extends PsiPackageImpl(pack.getManager.asInstanceOf[PsiManagerEx],
        pack.getQualifiedName) with ScPackage {

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    if (!pack.processDeclarations(processor, state, lastParent, place)) return false
    /*pack match {
      case synth: ScSyntheticPackage =>
      case _ =>
        val synth = ScSyntheticPackage.get(getQualifiedName, getProject)
        if (synth != null && !synth.processDeclarations(processor, state, lastParent, place)) return false
    }*/

    //for Scala
    if (place.getLanguage == ScalaFileType.SCALA_LANGUAGE) {
      //Process synthetic classes for scala._ package
      if (pack.getQualifiedName == "scala") {
        /**
         * Does the "scala" package already contain a class named `className`?
         *
         * @see http://youtrack.jetbrains.net/issue/SCL-2913
         */
        def alreadyContains(className: String) = pack match {
          case psiPackImpl: PsiPackageImpl => psiPackImpl.containsClassNamed(className)
          case _ => false
        }

        for (synth <- SyntheticClasses.get(getProject).getAll) {
          if (!alreadyContains(synth.getName)) processor.execute(synth, ResolveState.initial)
        }
        for (synthObj <- SyntheticClasses.get(getProject).syntheticObjects) {
          // Assume that is the scala package contained a class with the same names as the synthetic object, then it must also contain the object.
          // TODO Find a better way to directly check if the object already exists.
          if (!alreadyContains(synthObj.getName)) processor.execute(synthObj, ResolveState.initial)
        }
      }

      if (getQualifiedName == "scala") {
        val iterator: Iterator[PsiClass] = ImplicitlyImported.implicitlyImportedObjects(place.getManager,
          place.getResolveScope, "scala").iterator
        while (!iterator.isEmpty) {
          val obj = iterator.next
          if (!obj.processDeclarations(processor, state, lastParent, place)) return false
        }
      } else {
        findPackageObject(place.getResolveScope) match {
          case Some(obj) =>
            if (!obj.processDeclarations(processor, state, lastParent, place)) return false
          case None =>
        }
      }
    }
    return true
  }

  def findPackageObject(scope: GlobalSearchScope): Option[ScTypeDefinition] = {
    val manager = ScalaCachesManager.getInstance(getProject)

    var tuple = pack.getUserData(CachesUtil.PACKAGE_OBJECT_KEY)
    val count = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (tuple == null || tuple._2.longValue != count) {
      val cache = manager.getNamesCache
      val clazz = cache.getPackageObjectByName(getQualifiedName, scope)
      tuple = (clazz, java.lang.Long.valueOf(count)) // TODO is it safe to cache this ignoreing `scope`?
      pack.putUserData(CachesUtil.PACKAGE_OBJECT_KEY, tuple)
    }
    Option(tuple._1)
  }

  override def getParentPackage: PsiPackage = {
    val myQualifiedName = getQualifiedName
    val myManager = getManager
    if (myQualifiedName.length == 0) return null
    val lastDot: Int = myQualifiedName.lastIndexOf('.')
    if (lastDot < 0) {
      return ScPackageImpl.findPackage(getProject, "")
    } else {
      return ScPackageImpl.findPackage(getProject, myQualifiedName.substring(0, lastDot))
    }
  }

  override def getSubPackages: Array[PsiPackage] = {
    super.getSubPackages.map(ScPackageImpl(_))
  }

  override def getSubPackages(scope: GlobalSearchScope): Array[PsiPackage] = {
    super.getSubPackages(scope).map(ScPackageImpl(_))
  }
}

object ScPackageImpl {
  def apply(pack: PsiPackage): ScPackageImpl = {
    if (pack == null) null
    else if (pack.isInstanceOf[ScPackageImpl]) pack.asInstanceOf[ScPackageImpl]
    else new ScPackageImpl(pack)
  }

  def findPackage(project: Project, pName: String) = {
    ScPackageImpl(JavaPsiFacade.getInstance(project).findPackage(pName))
  }
}