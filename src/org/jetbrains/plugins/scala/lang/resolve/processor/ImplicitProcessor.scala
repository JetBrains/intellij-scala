package org.jetbrains.plugins.scala
package lang.resolve.processor

import java.util

import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.Set

/**
 * @author Alexander Podkhalyuzin
 */

/**
 * This class mark processor that only implicit object important among all PsiClasses
 */
abstract class ImplicitProcessor(kinds: Set[Value], withoutPrecedence: Boolean)
                                (implicit override val typeSystem: TypeSystem) extends BaseProcessor(kinds) with PrecedenceHelper[String] {
  protected val precedence: util.HashMap[String, Int] = new util.HashMap[String, Int]()
  protected val levelMap: util.HashMap[String, util.HashSet[ScalaResolveResult]] = new util.HashMap[String, util.HashSet[ScalaResolveResult]]()

  protected def getQualifiedName(result: ScalaResolveResult): String = {
    result.isRenamed match {
      case Some(str) => str
      case None => result.name
    }
  }

  protected def getTopPrecedence(result: ScalaResolveResult): Int = Option(precedence.get(getQualifiedName(result))).getOrElse(0)

  protected def setTopPrecedence(result: ScalaResolveResult, i: Int) {
    precedence.put(getQualifiedName(result), i)
  }

  override protected def clearLevelQualifiedSet(result: ScalaResolveResult) {
    //optimisation, do nothing
  }

  override protected def getLevelSet(result: ScalaResolveResult): util.HashSet[ScalaResolveResult] = {
    val qualifiedName = getQualifiedName(result)
    var levelSet = levelMap.get(qualifiedName)
    if (levelSet == null) {
      levelSet = new util.HashSet[ScalaResolveResult]
      levelMap.put(qualifiedName, levelSet)
    }
    levelSet
  }

  override protected def addResults(results: Seq[ScalaResolveResult]): Boolean = {
    if (withoutPrecedence) {
      candidatesSet ++= results
      true
    } else super.addResults(results)
  }

  override def changedLevel: Boolean = {
    if (levelMap.isEmpty) return true
    val iterator = levelMap.values().iterator()
    while (iterator.hasNext) {
      val setIterator = iterator.next().iterator()
      while (setIterator.hasNext) {
        candidatesSet += setIterator.next
      }
    }
    qualifiedNamesSet.addAll(levelQualifiedNamesSet)
    levelMap.clear()
    levelQualifiedNamesSet.clear()
    true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    val res = candidatesSet
    val iterator = levelMap.values().iterator()
    while (iterator.hasNext) {
      val setIterator = iterator.next().iterator()
      while (setIterator.hasNext) {
        candidatesSet += setIterator.next
      }
    }
    res
  }

  override protected def filterNot(p: ScalaResolveResult, n: ScalaResolveResult): Boolean = {
    getQualifiedName(p) == getQualifiedName(n) && super.filterNot(p, n)
  }

  override protected def isCheckForEqualPrecedence = false

  override def isImplicitProcessor: Boolean = true
}