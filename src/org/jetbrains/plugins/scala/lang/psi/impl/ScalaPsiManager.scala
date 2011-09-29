package org.jetbrains.plugins.scala
package lang
package psi
package impl

import api.statements.params.ScTypeParam
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import com.intellij.psi.impl.PsiManagerEx
import toplevel.synthetic.{SyntheticPackageCreator, ScSyntheticPackage}
import types._
import com.intellij.openapi.util.Key
import com.intellij.psi.search.GlobalSearchScope
import api.toplevel.typedef.ScObject
import com.intellij.ProjectTopics
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import com.intellij.reference.SoftReference
import caches.ScalaCachesManager
import collection.Seq
import java.util.Map
import com.intellij.psi._
import com.intellij.util.containers.WeakValueHashMap
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

class ScalaPsiManager(project: Project) extends ProjectComponent {
  private val implicitObjectMap: ConcurrentMap[String, SoftReference[java.util.Map[GlobalSearchScope, Seq[ScObject]]]] =
    new ConcurrentHashMap()

  private val classMap: ConcurrentMap[String, SoftReference[Map[GlobalSearchScope, Option[PsiClass]]]] =
    new ConcurrentHashMap()

  private val classesMap: ConcurrentMap[String, SoftReference[Map[GlobalSearchScope, Array[PsiClass]]]] =
    new ConcurrentHashMap()

  private val inheritorsMap: ConcurrentMap[PsiClass, SoftReference[ConcurrentMap[PsiClass, java.lang.Boolean]]] =
    new ConcurrentHashMap()
  
  def cachedDeepIsInheritor(clazz: PsiClass, base: PsiClass): Boolean = {
    val ref = inheritorsMap.get(clazz)
    var map: ConcurrentMap[PsiClass, java.lang.Boolean] = null
    if (ref == null || ref.get() == null) {
      map = new ConcurrentHashMap()
      inheritorsMap.put(clazz, new SoftReference(map))
    } else map = ref.get()

    val b = map.get(base)
    if (b != null) return b.booleanValue()

    val result = clazz.isInheritor(base, true)
    map.put(base, result)
    result
  }

  def getPackageImplicitObjects(fqn: String, scope: GlobalSearchScope): Seq[ScObject] = {
    def calc(): Seq[ScObject] = {
      ScalaCachesManager.getInstance(project).getNamesCache.getImplicitObjectsByPackage(fqn, scope).toSeq
    }

    val reference = implicitObjectMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Seq[ScObject]]()
      map.put(scope, calc())
      implicitObjectMap.put(fqn, new SoftReference(map))
      map
    } else reference.get()
    var result = map.get(scope)
    if (result == null) {
      result = calc()
      map.put(scope, result)
    }
    result
  }

  def getCachedClass(scope: GlobalSearchScope, fqn: String): PsiClass = {
    def calc(): Option[PsiClass] = Option(JavaPsiFacade.getInstance(project).findClass(fqn, scope))

    val reference = classMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Option[PsiClass]]()
      map.put(scope, calc())
      classMap.put(fqn, new SoftReference(map))
      map
    } else reference.get()
    var result = map.get(scope)
    if (result == null) {
      result = calc()
      map.put(scope, result)
    }
    result.getOrElse(null)
  }

  def getCachedClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass] = {
    def calc(): Array[PsiClass] = JavaPsiFacade.getInstance(project).findClasses(fqn, scope)

    val reference = classesMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Array[PsiClass]]()
      map.put(scope, calc())
      classesMap.put(fqn, new SoftReference(map))
      map
    } else reference.get()
    var result = map.get(scope)
    if (result == null) {
      result = calc()
      map.put(scope, result)
    }
    result
  }

  def projectOpened() {}
  def projectClosed() {}
  def getComponentName = "ScalaPsiManager"
  def disposeComponent() {}
  def initComponent() {
    PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].registerRunnableToRunOnAnyChange(new Runnable {
      override def run() {
        syntheticPackages.clear()
      }
    })

    PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].registerRunnableToRunOnChange(new Runnable {
      def run() {
        implicitObjectMap.clear()
        classMap.clear()
        inheritorsMap.clear()
      }
    })


    project.getMessageBus.connect.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener {
      def beforeRootsChange(event: ModuleRootEvent) {
      }

      def rootsChanged(event: ModuleRootEvent) {
        implicitObjectMap.clear()
        classMap.clear()
      }
    })
  }

  private val syntheticPackagesCreator = new SyntheticPackageCreator(project)
  private val syntheticPackages = new WeakValueHashMap[String, Any]

  def syntheticPackage(fqn : String) : ScSyntheticPackage = {
    var p = syntheticPackages.get(fqn)
    if (p == null) {
      p = syntheticPackagesCreator.getPackage(fqn)
      if (p == null) p = Null
      synchronized {
        val pp = syntheticPackages.get(fqn)
        if (pp == null) {
          syntheticPackages.put(fqn, p)
        } else {
          p = pp
        }
      }
    }

    p match {case synth : ScSyntheticPackage => synth case _ => null}
  }

  def typeVariable(tp: PsiTypeParameter) : ScTypeParameterType = {
    import Misc.fun2suspension
    tp match {
      case stp: ScTypeParam => {
        val inner = stp.typeParameters.map{typeVariable(_)}.toList
        val lower = () => stp.lowerBound.getOrNothing
        val upper = () => stp.upperBound.getOrAny
        // todo rework for error handling!
        val res = new ScTypeParameterType(stp.name, inner, lower, upper, stp)
        res
      }
      case _ => {
        val lower = () => Nothing
        val upper = () => tp.getSuperTypes match {
          case array: Array[PsiClassType] if array.length == 1 => ScType.create(array(0), project)
          case many => new ScCompoundType(collection.immutable.Seq(many.map {
            ScType.create(_, project)
          }.toSeq: _*),
            Seq.empty, Seq.empty, ScSubstitutor.empty)
        }
        val res = new ScTypeParameterType(tp.getName, Nil, lower, upper, tp)
        res
      }
    }
  }
}

object ScalaPsiManager {
  val TYPE_VARIABLE_KEY: Key[ScTypeParameterType] = Key.create("type.variable.key")

  def instance(project : Project) = project.getComponent(classOf[ScalaPsiManager])

  def typeVariable(tp : PsiTypeParameter): ScTypeParameterType = instance(tp.getProject).typeVariable(tp)
}