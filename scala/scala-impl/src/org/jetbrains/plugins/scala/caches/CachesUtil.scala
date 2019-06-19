package org.jetbrains.plugins.scala
package caches


import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util._
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.util._
import com.intellij.util.containers.{ContainerUtil, Stack}
import org.jetbrains.plugins.scala.caches.ProjectUserDataHolder._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl

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
   * Do not delete this type alias, it is used by [[org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard]]
    *
    * @see [[CachesUtil.getOrCreateKey]] for more info
   */
  type CachedMap[Data, Result] = CachedValue[ConcurrentMap[Data, Result]]
  type CachedRef[Result] = CachedValue[AtomicReference[Result]]
  private val keys = ContainerUtil.newConcurrentMap[String, Key[_]]()

  /**
   * IMPORTANT:
   * Cached annotations (CachedWithRecursionGuard, CachedMappedWithRecursionGuard, and CachedInUserData)
   * rely on this method, even though it shows that it is unused
   *
   * If you change this method in any way, please make sure it's consistent with the annotations
   *
   * Do not use this method directly. You should use annotations instead
   */
  def getOrCreateKey[T](id: String): Key[T] = {
    keys.get(id) match {
      case null =>
        val newKey = Key.create[T](id)
        val race = keys.putIfAbsent(id, newKey)

        if (race != null) race.asInstanceOf[Key[T]]
        else newKey
      case v => v.asInstanceOf[Key[T]]
    }
  }

  def libraryAwareModTracker(element: PsiElement): ModificationTracker = {
    val rootManager = ProjectRootManager.getInstance(element.getProject)
    element.getContainingFile match {
      case file: ScalaFile if file.isCompiled && rootManager.getFileIndex.isInLibrary(file.getVirtualFile) => rootManager
      case _: ClsFileImpl => rootManager
      case _ => enclosingModificationOwner(element)
    }
  }

  def enclosingModificationOwner(element: PsiElement): ModificationTracker = {

    if (!element.isValid) {
      return ModificationTracker.NEVER_CHANGED
    }

    new BlockModificationTracker(element)
  }

  @tailrec
  private def contextsWithExplicitType(element: PsiElement, result: List[ScExpression] = Nil): List[ScExpression] =
    contextWithExplicitType(element) match {
      case Some(expr) => contextsWithExplicitType(expr.getContext, expr :: result)
      case None       => result
    }

  @tailrec
  private def contextWithExplicitType(element: PsiElement): Option[ScExpression] =
    originalPosition(element) match {
      case null | _: ScalaFile => None
      case owner: ScExpression if owner.shouldntChangeModificationCount => Some(owner)
      case owner => contextWithExplicitType(owner.getContext)
    }


  private class BlockModificationTracker(element: PsiElement) extends ModificationTracker {
    private val topLevel = scalaTopLevelModTracker(element.getProject)

    def getModificationCount: Long = {
      //to avoid intermediate list allocation
      var result = topLevel.getModificationCount
      contextsWithExplicitType(element.getContext).foreach {
        result += _.modificationCount
      }
      result
    }
  }

  case class ProbablyRecursionException[Data](elem: PsiElement,
                                              data: Data,
                                              key: Key[_],
                                              set: Set[ScFunction]) extends ControlThrowable

  def getOrCreateCachedMap[Dom: ProjectUserDataHolder, Data, Result](elem: Dom,
                                                                     key: Key[CachedMap[Data, Result]],
                                                                     dependencyItem: () => Object): ConcurrentMap[Data, Result] = {

    val cachedValue = elem.getUserData(key) match {
      case null =>
        val manager = CachedValuesManager.getManager(elem.getProject)
        val provider = new CachedValueProvider[ConcurrentMap[Data, Result]] {
          def compute(): CachedValueProvider.Result[ConcurrentMap[Data, Result]] =
            new CachedValueProvider.Result(ContainerUtil.newConcurrentMap(), dependencyItem())
        }
        val newValue = manager.createCachedValue(provider, false)
        elem.putUserDataIfAbsent(key, newValue)
      case d => d
    }
    cachedValue.getValue
  }

  def getOrCreateCachedRef[Dom: ProjectUserDataHolder, Result](elem: Dom,
                                                               key: Key[CachedRef[Result]],
                                                               dependencyItem: () => Object): AtomicReference[Result] = {
    val cachedValue = elem.getUserData(key) match {
      case null =>
        val manager = CachedValuesManager.getManager(elem.getProject)
        val provider = new CachedValueProvider[AtomicReference[Result]] {
          def compute(): CachedValueProvider.Result[AtomicReference[Result]] =
            new CachedValueProvider.Result(new AtomicReference[Result](), dependencyItem())
        }
        val newValue = manager.createCachedValue(provider, false)
        elem.putUserDataIfAbsent(key, newValue)
      case d => d
    }
    cachedValue.getValue
  }

  //used in CachedWithRecursionGuard
  def handleRecursiveCall[Data, Result](e: PsiElement,
                                        data: Data,
                                        key: Key[_],
                                        defaultValue: => Result): Result = {
    ScObjectImpl.checkPackageObject()

    val function = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
    if (function == null || function.isProbablyRecursive) {
      defaultValue
    } else {
      function.isProbablyRecursive = true
      throw ProbablyRecursionException(e, data, key, Set(function))
    }
  }

  //Tuple2 class doesn't have half-specialized variants, so (T, Long) almost always have boxed long inside
  case class Timestamped[@specialized(Boolean, Int, AnyRef) T](data: T, modCount: Long)

  def fileModCount(file: PsiFile): Long =  {
    val topLevel = scalaTopLevelModTracker(file.getProject).getModificationCount
    topLevel + file.getModificationStamp
  }

  def scalaTopLevelModTracker(project: Project): ModificationTracker =
    ScalaPsiManager.instance(project).TopLevelModificationTracker

  private val originalPositionKey: Key[PsiElement] = Key.create("original.position.completion.key")

  def setOriginalPosition(element: PsiElement, original: PsiElement): Unit = {
    if (original != null) {
      for {
        elementContext  <- contextWithExplicitType(element)
        originalContext <- contextWithExplicitType(original)
      } {
        elementContext.putUserDataIfAbsent(originalPositionKey, originalContext)
      }
//      assert(enclosingModificationOwner(element).getModificationCount == enclosingModificationOwner(original).getModificationCount)
    }
  }

  private def originalPosition(element: PsiElement): PsiElement =
    Option(element)
      .flatMap(e => Option(e.getUserData(originalPositionKey)))
      .getOrElse(element)
}
