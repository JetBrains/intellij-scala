package org.jetbrains.plugins.scala.lang
package resolve
package processor
package precedence

import java.util

import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.psi.{PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

import scala.annotation.tailrec

/**
  * User: Alexander Podkhalyuzin
  * Date: 01.12.11
  */
//todo: logic is too complicated, too many connections between classes. Rewrite?
trait PrecedenceHelper[T] {
  this: BaseProcessor =>

  import PrecedenceHelper._

  def getPlace: PsiElement

  protected lazy val placePackageName: String = ResolveUtils.getPlacePackage(getPlace)
  protected val levelSet: util.HashSet[ScalaResolveResult] = new util.HashSet
  protected val qualifiedNamesSet: util.HashSet[T] = new util.HashSet[T]
  protected val levelQualifiedNamesSet: util.HashSet[T] = new util.HashSet[T]

  protected def clear(): Unit = {
    candidatesSet.clear()
    levelQualifiedNamesSet.clear()
    qualifiedNamesSet.clear()
    levelSet.clear()
  }

  protected def getQualifiedName(result: ScalaResolveResult): T

  private lazy val suspiciousPackages: Set[String] = collectPackages(getPlace)

  protected def ignored(results: Seq[ScalaResolveResult]): Boolean =
    results.headOption.flatMap(findQualifiedName)
      .exists((IgnoredPackages ++ suspiciousPackages).contains)

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
    if (results.isEmpty) return true
    val result: ScalaResolveResult = results.head
    lazy val qualifiedName: T = getQualifiedName(result)
    lazy val levelSet = getLevelSet(result)

    def addResults() {
      if (qualifiedName != null) levelQualifiedNamesSet.add(qualifiedName)
      val iterator = results.iterator
      while (iterator.hasNext) {
        levelSet.add(iterator.next())
      }
    }

    val currentPrecedence = getPrecedence(result)
    val topPrecedence = getTopPrecedence(result)
    if (currentPrecedence < topPrecedence) return false
    else if (currentPrecedence == topPrecedence && levelSet.isEmpty) return false
    else if (currentPrecedence == topPrecedence) {
      if (isCheckForEqualPrecedence && qualifiedName != null &&
        (levelQualifiedNamesSet.contains(qualifiedName) ||
          qualifiedNamesSet.contains(qualifiedName))) {
        return false
      } else if (qualifiedName != null && qualifiedNamesSet.contains(qualifiedName)) return false
      if (!ignored(results)) addResults()
    } else {
      if (qualifiedName != null && qualifiedNamesSet.contains(qualifiedName)) {
        return false
      } else {
        if (!ignored(results)) {
          setTopPrecedence(result, currentPrecedence)
          val levelSetIterator = levelSet.iterator()
          while (levelSetIterator.hasNext) {
            val next = levelSetIterator.next()
            if (filterNot(next, result)) {
              levelSetIterator.remove()
            }
          }
          clearLevelQualifiedSet(result)
          addResults()
        }
      }
    }
    true
  }

  protected def getPrecedence(result: ScalaResolveResult): Int = {
    specialPriority match {
      case Some(priority) => priority
      case None if result.prefixCompletion => PrecedenceTypes.PREFIX_COMPLETION
      case None => result.getPrecedence(getPlace, placePackageName)
    }
  }
}

object PrecedenceHelper {

  private val IgnoredPackages: Set[String] =
    Set("java.lang", "scala", "scala.Predef")

  private def collectPackages(element: PsiElement): Set[String] = {
    @tailrec
    def collectPackages(element: PsiElement, result: Set[String] = Set.empty): Set[String] =
      getContextOfType(element, true, classOf[ScPackaging]) match {
        case packaging: ScPackaging => collectPackages(packaging, result + packaging.fullPackageName)
        case null => result
      }

    collectPackages(element)
  }

  private def findQualifiedName(result: ScalaResolveResult): Option[String] =
    findImportReference(result)
      .flatMap(_.bind())
      .map(_.element)
      .collect {
        case p: PsiPackage => p.getQualifiedName
        case o: ScObject => o.qualifiedName
      }

  private def findImportReference(result: ScalaResolveResult): Option[ScStableCodeReferenceElement] =
    result.importsUsed.toSeq match {
      case Seq(head) =>
        val importExpression = head match {
          case ImportExprUsed(expr) => expr
          case ImportSelectorUsed(selector) => getContextOfType(selector, true, classOf[ScImportExpr])
          case ImportWildcardSelectorUsed(expr) => expr
        }
        Some(importExpression.qualifier)
      case _ => None
    }
}