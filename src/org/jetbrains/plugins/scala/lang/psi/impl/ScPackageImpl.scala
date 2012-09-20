package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.psi.impl.file.PsiPackageImpl
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi._
import impl.PsiManagerEx
import java.lang.String
import scope.PsiScopeProcessor
import toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.caches.{ScalaShortNamesCacheManager, CachesUtil}
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor, ImplicitProcessor}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import scala.util.control.ControlThrowable

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.04.2010
 */
class ScPackageImpl(val pack: PsiPackage) extends PsiPackageImpl(pack.getManager.asInstanceOf[PsiManagerEx],
        pack.getQualifiedName) with ScPackage {
  def superProcessDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                    lastParent: PsiElement, place: PsiElement): Boolean = {
    super.processDeclarations(processor, state, lastParent, place)
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    if (place.getLanguage == ScalaFileType.SCALA_LANGUAGE && pack.getQualifiedName == "scala") {
      if (!processor.isInstanceOf[ImplicitProcessor]) {
        val scope = processor match {
          case r: ResolveProcessor => r.getResolveScope
          case _ => place.getResolveScope
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
    if (place.getLanguage == ScalaFileType.SCALA_LANGUAGE) {
      val scope = processor match {
        case r: ResolveProcessor => r.getResolveScope
        case _ => place.getResolveScope
      }
      if (getQualifiedName == "scala") {
        ImplicitlyImported.implicitlyImportedObject(place.getManager, scope, "scala") match {
          case Some(obj: ScObject) =>
            var newState = state
            obj.getType(TypingContext.empty).foreach {
              case tp: ScType => newState = state.put(BaseProcessor.FROM_TYPE_KEY, tp)
            }
            if (!obj.processDeclarations(processor, newState, lastParent, place)) return false
          case _ =>
        }
      } else {
        findPackageObject(scope) match {
          case Some(obj: ScObject) =>
            var newState = state
            obj.getType(TypingContext.empty).foreach {
              case tp: ScType => newState = state.put(BaseProcessor.FROM_TYPE_KEY, tp)
            }
            import ScPackageImpl._
            startPackageObjectProcessing()
            try {
            if (!obj.processDeclarations(processor, newState, lastParent, place)) return false
            } catch {
              case ignore: DoNotProcessPackageObjectException => //do nothing, just let's move on
            } finally {
              stopPackageObjectProcessing()
            }
          case _ =>
        }
      }
    }
    true
  }

  def findPackageObject(scope: GlobalSearchScope): Option[ScTypeDefinition] = {
    val manager = ScalaShortNamesCacheManager.getInstance(getProject)

    var tuple = pack.getUserData(CachesUtil.PACKAGE_OBJECT_KEY)
    val count = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (tuple == null || tuple._2.longValue != count) {
      val clazz = manager.getPackageObjectByName(getQualifiedName, scope)
      tuple = (clazz, java.lang.Long.valueOf(count)) // TODO is it safe to cache this ignoring `scope`?
      pack.putUserData(CachesUtil.PACKAGE_OBJECT_KEY, tuple)
    }
    Option(tuple._1)
  }

  override def getParentPackage: PsiPackageImpl = {
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

  class DoNotProcessPackageObjectException extends ControlThrowable

  def isPackageObjectProcessing: Boolean = {
    processing.get() > 0
  }

  def startPackageObjectProcessing() {
    processing.set(processing.get() + 1)
  }

  def stopPackageObjectProcessing() {
    processing.set(processing.get() - 1)
  }

  private val processing: ThreadLocal[Long] = new ThreadLocal[Long] {
    override def initialValue(): Long = 0
  }
}