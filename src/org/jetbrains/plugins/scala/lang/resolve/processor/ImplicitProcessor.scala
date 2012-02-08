package org.jetbrains.plugins.scala.lang.resolve.processor

import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import collection.mutable.HashMap
import collection.Set

/**
 * @author Alexander Podkhalyuzin
 */

/**
 * This class mark processor that only implicit object important among all PsiClasses
 */
abstract class ImplicitProcessor(kinds: Set[Value], withoutPrecedence: Boolean) extends BaseProcessor(kinds) with PrecedenceHelper {
  protected val precedence: HashMap[String, Int] = new HashMap[String, Int]()

  protected def getQualifiedName(result: ScalaResolveResult): String = {
    result.isRenamed match {
      case Some(str) => str
      case None => result.name
    }
  }

  protected def getTopPrecedence(result: ScalaResolveResult): Int = precedence.getOrElse(getQualifiedName(result), 0)

  protected def setTopPrecedence(result: ScalaResolveResult, i: Int) {
    precedence.put(getQualifiedName(result), i)
  }

  override protected def addResults(results: Seq[ScalaResolveResult]): Boolean = {
    if (withoutPrecedence) {
      candidatesSet ++= results
      true
    } else super.addResults(results)
  }

  override def changedLevel: Boolean = {
    if (levelSet.isEmpty) return true
    val iterator = levelSet.iterator()
    while (iterator.hasNext) {
      candidatesSet += iterator.next()
    }
    qualifiedNamesSet ++= levelQualifiedNamesSet
    levelSet.clear()
    levelQualifiedNamesSet.clear()
    true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    val res = candidatesSet
    val iterator = levelSet.iterator()
    while (iterator.hasNext) {
      res += iterator.next()
    }
    res
  }

  override protected def filterNot(p: ScalaResolveResult, n: ScalaResolveResult): Boolean = {
    getQualifiedName(p) == getQualifiedName(n) && super.filterNot(p, n)
  }

  override protected def isCheckForEqualPrecedence = false
}