package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.caches.stats.Tracer
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.Measure

import scala.collection.{Seq, Set}

/**
  * @author Nikolay.Tropin
  */
class ImplicitCollectorCache(project: Project) {
  private val map =
    ContainerUtil.newConcurrentMap[(ImplicitSearchScope, ScType), Seq[ScalaResolveResult]]()

  private val visibleImplicitsMap =
    ContainerUtil.newConcurrentMap[ImplicitSearchScope, Set[ScalaResolveResult]]()

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
    visibleImplicitsMap.computeIfAbsent(scope,
      _ =>
        try {
          tracer.calculationStart()
          new ImplicitParametersProcessor(place, withoutPrecedence = false).candidatesByPlace
        } finally {
          tracer.calculationEnd()
        }
    )
  }

  def put(place: PsiElement, tp: ScType, value: Seq[ScalaResolveResult]): Unit = {
    val scope = ImplicitSearchScope.forElement(place)
    map.put((scope, tp), value)
  }

  def size(): Int = map.size() + visibleImplicitsMap.size()

  def clear(): Unit = {
    map.clear()
    visibleImplicitsMap.clear()
  }
}
