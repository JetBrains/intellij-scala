package org.jetbrains.plugins.scala.lang
package resolve
package processor
package precedence

import java.util

import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.psi.{PsiElement, PsiPackage}
import com.intellij.util.containers.SmartHashSet
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
trait PrecedenceHelper {

  import PrecedenceHelper._

  def getPlace: PsiElement

  protected lazy val placePackageName: String = ResolveUtils.getPlacePackage(getPlace)

  protected val holder: TopPrecedenceHolder

  protected def nameUniquenessStrategy: NameUniquenessStrategy

  protected val levelSet           : util.Set[ScalaResolveResult] = new SmartHashSet()
  protected val uniqueNamesSet     : util.Set[ScalaResolveResult] = new UniqueNamesSet(nameUniquenessStrategy)
  protected val levelUniqueNamesSet: util.Set[ScalaResolveResult] = new UniqueNamesSet(nameUniquenessStrategy)

  protected def clear(): Unit = {
    levelUniqueNamesSet.clear()
    uniqueNamesSet.clear()
    levelSet.clear()
  }

  private lazy val suspiciousPackages: Set[String] = collectPackages(getPlace)

  protected def ignored(results: Seq[ScalaResolveResult]): Boolean =
    results.headOption.flatMap(findQualifiedName)
      .exists((IgnoredPackages ++ suspiciousPackages).contains)

  protected def isCheckForEqualPrecedence = true

  protected def clearLevelQualifiedSet(result: ScalaResolveResult) {
    levelUniqueNamesSet.clear()
  }

  protected def getLevelSet(result: ScalaResolveResult): util.Set[ScalaResolveResult] = levelSet

  /**
    * Do not add ResolveResults through candidatesSet. It may break precedence. Use this method instead.
    */
  protected def addResult(result: ScalaResolveResult): Boolean = addResults(Seq(result))

  protected def addResults(results: Seq[ScalaResolveResult]): Boolean = {
    if (results.isEmpty) return true
    val result: ScalaResolveResult = results.head

    lazy val levelSet = getLevelSet(result)

    def addResults() {
      levelUniqueNamesSet.add(result)
      val iterator = results.iterator
      while (iterator.hasNext) {
        levelSet.add(iterator.next())
      }
    }

    val currentPrecedence = precedence(result)
    val topPrecedence = holder(result)
    if (currentPrecedence < topPrecedence) return false
    else if (currentPrecedence == topPrecedence && levelSet.isEmpty) return false
    else if (currentPrecedence == topPrecedence) {
      if (isCheckForEqualPrecedence &&
        (levelUniqueNamesSet.contains(result) || uniqueNamesSet.contains(result))) {
        return false
      } else if (uniqueNamesSet.contains(result)) return false
      if (!ignored(results)) addResults()
    } else {
      if (uniqueNamesSet.contains(result)) {
        return false
      } else {
        if (!ignored(results)) {
          holder(result) = currentPrecedence
          val levelSetIterator = levelSet.iterator()
          while (levelSetIterator.hasNext) {
            val next = levelSetIterator.next()
            if (holder.filterNot(next, result)(precedence)) {
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

  protected def precedence(result: ScalaResolveResult): Int =
    if (result.prefixCompletion) PrecedenceTypes.PREFIX_COMPLETION
    else result.getPrecedence(getPlace, placePackageName)
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