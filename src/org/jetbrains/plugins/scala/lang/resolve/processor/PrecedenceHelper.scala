package org.jetbrains.plugins.scala
package lang.resolve.processor

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import java.util

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.12.11
 */

trait PrecedenceHelper[T] {
  this: BaseProcessor =>

  protected def getPlace: PsiElement
  protected lazy val placePackageName: String = ResolveUtils.getPlacePackage(getPlace)
  protected val levelSet: util.HashSet[ScalaResolveResult] = new util.HashSet
  protected val qualifiedNamesSet: util.HashSet[T] = new util.HashSet[T]
  protected val levelQualifiedNamesSet: util.HashSet[T] = new util.HashSet[T]

  protected def getQualifiedName(result: ScalaResolveResult): T

  /**
   * Returns highest precedence of all resolve results.
   * 1 - import a._
   * 2 - import a.x
   * 3 - definition or declaration
   */
  protected def getTopPrecedence(result: ScalaResolveResult): Int
  protected def setTopPrecedence(result: ScalaResolveResult, i: Int)
  protected def filterNot(p: ScalaResolveResult, n: ScalaResolveResult): Boolean = {
    getPrecedence(p) < getTopPrecedence(n)
  }
  protected def isCheckForEqualPrecedence = true
  protected def clearLevelQualifiedSet(result: ScalaResolveResult) {
    levelQualifiedNamesSet.clear()
  }
  protected def getLevelSet(result: ScalaResolveResult): util.HashSet[ScalaResolveResult] = levelSet

  /**
   * Do not add ResolveResults through candidatesSet. It may break precedence. Use this method instead.
   */
  protected def addResult(result: ScalaResolveResult): Boolean = addResults(Seq(result))
  protected def addResults(results: Seq[ScalaResolveResult]): Boolean = {
    if (results.length == 0) return true
    lazy val qualifiedName: T = getQualifiedName(results(0))
    lazy val levelSet = getLevelSet(results(0))
    def addResults() {
      if (qualifiedName != null) levelQualifiedNamesSet.add(qualifiedName)
      val iterator = results.iterator
      while (iterator.hasNext) {
        levelSet.add(iterator.next())
      }
    }
    val currentPrecedence = getPrecedence(results(0))
    val topPrecedence = getTopPrecedence(results(0))
    if (currentPrecedence < topPrecedence) return false
    else if (currentPrecedence == topPrecedence && levelSet.isEmpty) return false
    else if (currentPrecedence == topPrecedence && !levelSet.isEmpty) {
      if (isCheckForEqualPrecedence && qualifiedName != null &&
        (levelQualifiedNamesSet.contains(qualifiedName) ||
        qualifiedNamesSet.contains(qualifiedName))) {
        return false
      } else if (qualifiedName != null && qualifiedNamesSet.contains(qualifiedName)) return false
      addResults()
    } else {
      if (qualifiedName != null && (levelQualifiedNamesSet.contains(qualifiedName) ||
        qualifiedNamesSet.contains(qualifiedName))) {
        return false
      } else {
        setTopPrecedence(results(0), currentPrecedence)
        val levelSetIterator = levelSet.iterator()
        while (levelSetIterator.hasNext) {
          val next = levelSetIterator.next()
          if (filterNot(next, results(0))) {
            levelSetIterator.remove()
          }
        }
        clearLevelQualifiedSet(results(0))
        addResults()
      }
    }
    true
  }

  protected def getPrecedence(result: ScalaResolveResult): Int = {
    specialPriority match {
      case Some(priority) => priority
      case None => result.getPrecedence(getPlace, placePackageName)
    }
  }
}

object PrecedenceHelper {
  object PrecedenceTypes {
    val JAVA_LANG = 1
    val SCALA = 2
    val SCALA_PREDEF = 3
    val PACKAGE_LOCAL_PACKAGE = 4
    val WILDCARD_IMPORT_PACKAGE = 5
    val IMPORT_PACKAGE = 6
    val PACKAGE_LOCAL = 7
    val WILDCARD_IMPORT = 8
    val IMPORT = 9
    val OTHER_MEMBERS = 10
  }
}