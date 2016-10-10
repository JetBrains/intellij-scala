package org.jetbrains.plugins.scala
package caches


import java.util.concurrent.ConcurrentMap

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util._
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.util._
import com.intellij.util.containers.{ContainerUtil, Stack}
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScModificationTrackerOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.annotation.tailrec
import scala.util.control.ControlThrowable

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.06.2009
 */
object CachesUtil {

  /** This value is used by cache analyzer
   *
   * @see [[org.jetbrains.plugins.scala.macroAnnotations.CachedMacroUtil.transformRhsToAnalyzeCaches]]
   */
  lazy val timeToCalculateForAnalyzingCaches: ThreadLocal[Stack[Long]] = new ThreadLocal[Stack[Long]] {
    override def initialValue: Stack[Long] = new Stack[Long]()
  }


  /**
   * Do not delete this type alias, it is used by [[org.jetbrains.plugins.scala.macroAnnotations.CachedMappedWithRecursionGuard]]
    *
    * @see [[CachesUtil.getOrCreateKey]] for more info
   */
  type MappedKey[Data, Result] = Key[CachedValue[ConcurrentMap[Data, Result]]]
  private val keys = ContainerUtil.newConcurrentMap[String, Any]()

  /**
   * IMPORTANT:
   * Cached annotations (CachedWithRecursionGuard, CachedMappedWithRecursionGuard, and CachedInsidePsiElement)
   * rely on this method, even though it shows that it is unused
   *
   * If you change this method in any way, please make sure it's consistent with the annotations
   *
   * Do not use this method directly. You should use annotations instead
   */
  def getOrCreateKey[T](id: String): T = Option(keys.get(id)) match {
    case Some(key) => key.asInstanceOf[T]
    case None => synchronized {
      Option(keys.get(id)) match {
        case Some(key) => key.asInstanceOf[T]
        case None =>
          val res: T = Key.create[T](id).asInstanceOf[T]
          keys.put(id, res)
          res
      }
    }
  }

  //keys for getUserData
  val IMPLICIT_TYPE: Key[ScType] = Key.create("implicit.type")
  val IMPLICIT_FUNCTION: Key[PsiNamedElement] = Key.create("implicit.function")
  val NAMED_PARAM_KEY: Key[java.lang.Boolean] = Key.create("named.key")
  val PACKAGE_OBJECT_KEY: Key[(ScTypeDefinition, java.lang.Long)] = Key.create("package.object.key")
  val PROJECT_HAS_DOTTY_KEY: Key[java.lang.Boolean] = Key.create("project.has.dotty")

  /**
   * IMPORTANT:
   * CachedWithRecursionGuard annotation relies on this method. If you delete this method a lot of the code will
   * If you change this method in any way, please make sure it's consistent with CachedWithRecursionGuard.
   *
   * Do not use this method directly. You should use CachedWithRecursionGuard annotation instead
   */
  def getWithRecursionPreventingWithRollback[Dom <: PsiElement, Result](e: Dom, key: Key[CachedValue[Result]],
                                                        provider: => MyProvider[Dom, Result],
                                                        defaultValue: => Result): Result = {
    var computed: CachedValue[Result] = e.getUserData(key)
    if (computed == null) {
      val manager = CachedValuesManager.getManager(e.getProject)
      computed = manager.createCachedValue(new CachedValueProvider[Result] {
        def compute(): CachedValueProvider.Result[Result] = {
          val guard = getRecursionGuard(key.toString)
          if (guard.currentStack().contains(e)) {
            if (ScPackageImpl.isPackageObjectProcessing) {
              throw new ScPackageImpl.DoNotProcessPackageObjectException
            }
            val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
            if (fun == null || fun.isProbablyRecursive) {
              return new CachedValueProvider.Result(defaultValue, provider.getDependencyItem)
            } else {
              fun.setProbablyRecursive(true)
              throw new ProbablyRecursionException(e, (), key, Set(fun))
            }
          }
          guard.doPreventingRecursion(e, false /* todo: true? */, new Computable[CachedValueProvider.Result[Result]] {
            def compute(): CachedValueProvider.Result[Result] = {
              try {
                provider.compute()
              }
              catch {
                case ProbablyRecursionException(`e`, (), k, set) if k == key =>
                  try {
                    provider.compute()
                  }
                  finally set.foreach(_.setProbablyRecursive(false))
                case t@ProbablyRecursionException(ee, data, k, set) if k == key =>
                  val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
                  if (fun == null || fun.isProbablyRecursive) throw t
                  else {
                    fun.setProbablyRecursive(true)
                    throw ProbablyRecursionException(ee, data, k, set + fun)
                  }
              }
            }
          }) match {
            case null => new CachedValueProvider.Result(defaultValue, provider.getDependencyItem)
            case notNull => notNull
          }
        }
      }, false)
      e.putUserData(key, computed)
    }
    computed.getValue
  }

  /**
   * IMPORTANT:
   * CachedInsidePsiElement annotation relies on this method. If you delete this method a lot of the code will
   * stop compiling even though the method is shown as unused.
   * If you change this method in any way, please make sure it's consistent with CachedInsidePsiElement.
   *
   * Do not use this method directly. You should use CachedInsidePsiElement annotation instead
   */
  def get[Dom <: PsiElement, T](e: Dom, key: Key[CachedValue[T]], provider: => CachedValueProvider[T]): T = {
    var computed: CachedValue[T] = e.getUserData(key)
    if (computed == null) {
      val manager = CachedValuesManager.getManager(e.getProject)
      computed = manager.createCachedValue(provider, false)
      e.putUserData(key, computed)
    }
    computed.getValue
  }

  class MyProvider[Dom, T](e: Dom, builder: Dom => T)(dependencyItem: Object) extends CachedValueProvider[T] {
    def getDependencyItem: Object = dependencyItem
    def compute() = new CachedValueProvider.Result(builder(e), dependencyItem)
  }

  private val guards: ConcurrentMap[String, RecursionGuard] = ContainerUtil.newConcurrentMap[String, RecursionGuard]()
  def getRecursionGuard(id: String): RecursionGuard = {
    val guard = guards.get(id)
    if (guard == null) {
      val result = RecursionManager.createGuard(id)
      guards.put(id, result)
      result
    } else guard
  }

  /**
   * IMPORTANT:
   * CachedMappedWithRecursionGuard annotation relies on this method. If you delete this method a lot of the code will
   * stop compiling even though the method is shown as unused.
   * If you change this method in any way, please make sure it's consistent with CachedMappedWithRecursionGuard.
   *
   * Do not use this method directly. You should use CachedMappedWithRecursionGuard annotation instead
   */
  def getMappedWithRecursionPreventingWithRollback[Dom <: PsiElement, Data, Result](e: Dom, data: Data,
                                                                        key: Key[CachedValue[ConcurrentMap[Data, Result]]],
                                                                        builder: (Dom, Data) => Result,
                                                                        defaultValue: => Result,
                                                                        dependencyItem: Object): Result = {
    var computed: CachedValue[ConcurrentMap[Data, Result]] = e.getUserData(key)
    if (computed == null) {
      val manager = CachedValuesManager.getManager(e.getProject)
      computed = manager.createCachedValue(new CachedValueProvider[ConcurrentMap[Data, Result]] {
        def compute(): CachedValueProvider.Result[ConcurrentMap[Data, Result]] = {
          new CachedValueProvider.Result(ContainerUtil.newConcurrentMap[Data, Result](), dependencyItem)
        }
      }, false)
      e.putUserData(key, computed)
    }
    val map = computed.getValue
    var result = map.get(data)
    if (result == null) {
      var isCache = true
      result = {
        val guard = getRecursionGuard(key.toString)
        if (guard.currentStack().contains((e, data))) {
          if (ScPackageImpl.isPackageObjectProcessing) {
            throw new ScPackageImpl.DoNotProcessPackageObjectException
          }
          val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
          if (fun == null || fun.isProbablyRecursive) {
            isCache = false
            defaultValue
          } else {
            fun.setProbablyRecursive(true)
            throw new ProbablyRecursionException(e, data, key, Set(fun))
          }
        } else {
          guard.doPreventingRecursion((e, data), false, new Computable[Result] {
            def compute(): Result = {
              try {
                builder(e, data)
              }
              catch {
                case ProbablyRecursionException(`e`, `data`, k, set) if k == key =>
                  try {
                    builder(e, data)
                  } finally set.foreach(_.setProbablyRecursive(false))
                case t@ProbablyRecursionException(ee, innerData, k, set) if k == key =>
                  val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
                  if (fun == null || fun.isProbablyRecursive) throw t
                  else {
                    fun.setProbablyRecursive(true)
                    throw ProbablyRecursionException(ee, innerData, k, set + fun)
                  }
              }
            }
          }) match {
            case null => defaultValue
            case notNull => notNull
          }
        }
      }
      if (isCache) {
        map.put(data, result)
      }
    }
    result
  }

  def getDependentItem(element: PsiElement)(dep_item: Object = enclosingModificationOwner(element)): Object = {
    element.getContainingFile match {
      case file: ScalaFile if file.isCompiled =>
        if (!ProjectRootManager.getInstance(element.getProject).getFileIndex.isInContent(file.getVirtualFile)) {
          return dep_item
        }
        var dir = file.getParent
        while (dir != null) {
          if (dir.getName == "scala-library.jar") return ModificationTracker.NEVER_CHANGED
          dir = dir.getParent
        }
        ProjectRootManager.getInstance(element.getProject)
      case _: ClsFileImpl => ProjectRootManager.getInstance(element.getProject)
      case _ => dep_item
    }
  }

  def enclosingModificationOwner(elem: PsiElement): ModificationTracker = {
    @tailrec
    def calc(element: PsiElement): ModificationTracker = {
      Option(PsiTreeUtil.getContextOfType(element, false, classOf[ScModificationTrackerOwner])) match {
        case Some(owner) if owner.isValidModificationTrackerOwner => owner.getModificationTracker
        case Some(owner) => calc(owner.getContext)
        case _ if elem != null => ScalaPsiManager.instance(elem.getProject).modificationTracker
        case _ => ScalaPsiManager.instance(element.getProject).modificationTracker
      }
    }

    calc(elem)
  }

  @tailrec
  def updateModificationCount(elem: PsiElement, incModCountOnTopLevel: Boolean = false): Unit = {
    Option(PsiTreeUtil.getContextOfType(elem, false, classOf[ScModificationTrackerOwner], classOf[ScalaCodeFragment])) match {
      case Some(_: ScalaCodeFragment) => //do not update on changes in dummy file
      case Some(owner: ScModificationTrackerOwner) if owner.isValidModificationTrackerOwner =>
        owner.incModificationCount()
      case Some(owner) => updateModificationCount(owner.getContext)
      case _ if incModCountOnTopLevel => ScalaPsiManager.instance(elem.getProject).incModificationCount()
      case _ =>
    }
  }

  case class ProbablyRecursionException[Dom <: PsiElement, Data, T](elem: Dom,
                                                                            data: Data,
                                                                            key: Key[T],
                                                                            set: Set[ScFunction]) extends ControlThrowable
}
