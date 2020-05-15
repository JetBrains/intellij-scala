package org.jetbrains.plugins.scala.lang.psi.implicits

import java.lang.System.identityHashCode
import java.util.concurrent.ConcurrentHashMap

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.caches.stats.Tracer
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.util.HashBuilder.toHashBuilder

import scala.collection.Seq
import scala.collection.Set

/**
  * @author Nikolay.Tropin
  */
class ImplicitCollectorCache(project: Project) {
  private val map =
    new ConcurrentHashMap[(ImplicitSearchScope, ScType), Seq[ScalaResolveResult]]()

  private val visibleImplicitsMap =
    new ConcurrentHashMap[ImplicitSearchScope, Set[ScalaResolveResult]]()

  private val nonValueTypesMap =
    new ConcurrentHashMap[NonValueTypesKey, NonValueFunctionTypes]()

  def get(place: PsiElement, tp: ScType): Option[Seq[ScalaResolveResult]] = {
    val scope = ImplicitSearchScope.forElement(place)
    Option(map.get((scope, tp)))
  }

  def getOrCompute(place: PsiElement, tp: ScType, mayCacheResult: Boolean)
                  (computation: => Seq[ScalaResolveResult]): Seq[ScalaResolveResult] = {

    val tracer = Tracer("ImplicitCollectorCache.getOrCompute", "ImplicitCollectorCache.getOrCompute")
    tracer.invocation()

    get(place, tp) match {
      case Some(cached) => return cached
      case _ =>
    }

    tracer.calculationStart()
    try {

      val stackStamp = RecursionManager.markStack()
      val result = computation

      if (mayCacheResult && stackStamp.mayCacheNow())
        put(place, tp, result)

      result

    } finally {
      tracer.calculationEnd()
    }
  }

  def getVisibleImplicits(place: PsiElement): Set[ScalaResolveResult] = {
    val tracer = Tracer("ImplicitCollectorCache.getVisibleImplicits", "ImplicitCollectorCache.getVisibleImplicits")
    tracer.invocation()

    val scope = ImplicitSearchScope.forElement(place)

    //computation is potentially recursive and it may lead to deadlock if it is executed in `computeIfAbsent`
    visibleImplicitsMap.get(scope) match {
      case null =>
        val result = try {
          tracer.calculationStart()
          new ImplicitParametersProcessor(place, withoutPrecedence = false).candidatesByPlace
        } finally {
          tracer.calculationEnd()
        }
        visibleImplicitsMap.computeIfAbsent(scope, _ => result)
      case v => v
    }
  }

  private[implicits] def getNonValueTypes(fun: ScFunction,
                                          substitutor: ScSubstitutor,
                                          typeFromMacro: Option[ScType]): NonValueFunctionTypes = {

    val key = NonValueTypesKey(fun, substitutor, typeFromMacro)
    nonValueTypesMap.computeIfAbsent(key, key => NonValueFunctionTypes(key.fun, key.substitutor, key.typeFromMacro))
  }

  def put(place: PsiElement, tp: ScType, value: Seq[ScalaResolveResult]): Unit = {
    val scope = ImplicitSearchScope.forElement(place)
    map.put((scope, tp), value)
  }

  def size(): Int = map.size() + visibleImplicitsMap.size() + nonValueTypesMap.size()

  def clear(): Unit = {
    map.clear()
    visibleImplicitsMap.clear()
    nonValueTypesMap.clear()
  }

  private case class NonValueTypesKey(fun: ScFunction, substitutor: ScSubstitutor, typeFromMacro: Option[ScType]) {
    override def hashCode(): Int = fun.hashCode() #+ identityHashCode(substitutor) #+ typeFromMacro

    override def equals(obj: Any): Boolean = obj match {
      case NonValueTypesKey(otherFun, otherSubst, otherType) =>
        otherFun == fun && (substitutor eq otherSubst) && typeFromMacro == otherType
      case _ =>
        false
    }
  }
}
