package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.psi.impl.file.PsiPackageImpl
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi._
import impl.PsiManagerEx
import scope.PsiScopeProcessor
import java.lang.String
import collection.Iterator
import scope.PsiScopeProcessor.Event
import toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.caches.{CachesUtil, ScalaCachesManager}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.util.Key
import collection.mutable.HashSet

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.04.2010
 */

class ScPackageImpl(pack: PsiPackage) extends PsiPackageImpl(pack.getManager.asInstanceOf[PsiManagerEx],
        pack.getQualifiedName) with ScPackage {

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    if (place.getLanguage == ScalaFileType.SCALA_LANGUAGE && pack.getQualifiedName == "scala") {
      val namesSet = new HashSet[String]

      if (!pack.processDeclarations(new PsiScopeProcessor {
        def execute(element: PsiElement, state: ResolveState): Boolean = {
          element match {
            case clazz: PsiClass => namesSet += clazz.getName
            case _ =>
          }
          processor.execute(element, state)
        }

        def getHint[T](hintKey: Key[T]): T = processor.getHint(hintKey)

        def handleEvent(event: Event, associated: AnyRef) {
          processor.handleEvent(event, associated)
        }
      }, state, lastParent, place)) return false

      //Process synthetic classes for scala._ package
      /**
       * Does the "scala" package already contain a class named `className`?
       *
       * @see http://youtrack.jetbrains.net/issue/SCL-2913
       */
      def alreadyContains(className: String) = namesSet.contains(className)

      for (synth <- SyntheticClasses.get(getProject).getAll) {
        if (!alreadyContains(synth.getName)) processor.execute(synth, ResolveState.initial)
      }
      for (synthObj <- SyntheticClasses.get(getProject).syntheticObjects) {

        // Assume that is the scala package contained a class with the same names as the synthetic object,
        // then it must also contain the object.
        if (!alreadyContains(synthObj.getName)) processor.execute(synthObj, ResolveState.initial)
      }
    } else if (!pack.processDeclarations(processor, state, lastParent, place)) return false

    //for Scala
    if (place.getLanguage == ScalaFileType.SCALA_LANGUAGE) {
      if (getQualifiedName == "scala") {
        val iterator: Iterator[PsiClass] = ImplicitlyImported.implicitlyImportedObjects(place.getManager,
          place.getResolveScope, "scala").iterator
        while (!iterator.isEmpty) {
          val obj = iterator.next()
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
    true
  }

  def findPackageObject(scope: GlobalSearchScope): Option[ScTypeDefinition] = {
    val manager = ScalaCachesManager.getInstance(getProject)

    var tuple = pack.getUserData(CachesUtil.PACKAGE_OBJECT_KEY)
    val count = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (tuple == null || tuple._2.longValue != count) {
      val cache = manager.getNamesCache
      val clazz = cache.getPackageObjectByName(getQualifiedName, scope)
      tuple = (clazz, java.lang.Long.valueOf(count)) // TODO is it safe to cache this ignoring `scope`?
      pack.putUserData(CachesUtil.PACKAGE_OBJECT_KEY, tuple)
    }
    Option(tuple._1)
  }

  override def getParentPackage: PsiPackage = {
    val myQualifiedName = getQualifiedName
    if (myQualifiedName.length == 0) return null
    val lastDot: Int = myQualifiedName.lastIndexOf('.')
    if (lastDot < 0) {
      ScPackageImpl.findPackage(getProject, "")
    } else {
      ScPackageImpl.findPackage(getProject, myQualifiedName.substring(0, lastDot))
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