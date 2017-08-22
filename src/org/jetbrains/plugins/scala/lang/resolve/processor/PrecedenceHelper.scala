package org.jetbrains.plugins.scala
package lang.resolve.processor

import java.util

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}

/**
  * User: Alexander Podkhalyuzin
  * Date: 01.12.11
  */
//todo: logic is too complicated, too many connections between classes. Rewrite?
trait PrecedenceHelper[T] {
  this: BaseProcessor =>

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

  private lazy val suspiciousPackages: Set[String] = {
    def collectPackages(elem: PsiElement, res: Set[String] = Set.empty): Set[String] = {
      PsiTreeUtil.getContextOfType(elem, true, classOf[ScPackaging]) match {
        case null => res
        case p: ScPackaging => collectPackages(p, res + p.fullPackageName)
      }
    }

    Set("scala", "java.lang", "scala", "scala.Predef") ++ collectPackages(getPlace)
  }

  protected def ignored(results: Seq[ScalaResolveResult]): Boolean =
    results.headOption.flatMap(PrecedenceHelper.findQualifiedName)
      .exists(suspiciousPackages.contains)

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
      case None if result.prefixCompletion => PrecedenceHelper.PrecedenceTypes.PREFIX_COMPLETION
      case None => result.getPrecedence(getPlace, placePackageName)
    }
  }
}

object PrecedenceHelper {

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
          case ImportSelectorUsed(selector) => PsiTreeUtil.getContextOfType(selector, true, classOf[ScImportExpr])
          case ImportWildcardSelectorUsed(expr) => expr
        }
        Some(importExpression.qualifier)
      case _ => None
    }

  object PrecedenceTypes {
    val PREFIX_COMPLETION = 0
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