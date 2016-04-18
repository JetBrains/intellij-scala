package org.jetbrains.plugins.scala
package lang.resolve.processor

import java.util

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.12.11
 */
//todo: logic is too complicated, too many connections between classes. Rewrite?
trait PrecedenceHelper[T] {
  this: BaseProcessor =>

  protected def getPlace: PsiElement
  protected lazy val placePackageName: String = ResolveUtils.getPlacePackage(getPlace)
  protected val levelSet: util.HashSet[ScalaResolveResult] = new util.HashSet
  protected val qualifiedNamesSet: util.HashSet[T] = new util.HashSet[T]
  protected val levelQualifiedNamesSet: util.HashSet[T] = new util.HashSet[T]
  protected val ignoredSet: util.HashSet[ScalaResolveResult] = new util.HashSet[ScalaResolveResult]

  protected sealed trait HistoryEvent
  protected case object ChangedLevel extends HistoryEvent
  protected case class AddResult(results: Seq[ScalaResolveResult]) extends HistoryEvent

  protected val history: ArrayBuffer[HistoryEvent] = new ArrayBuffer
  private var fromHistory: Boolean = false

  protected def compareWithIgnoredSet(set: mutable.HashSet[ScalaResolveResult])
                                     (implicit typeSystem: TypeSystem): Boolean = {
    import scala.collection.JavaConversions._
    if (ignoredSet.nonEmpty && set.isEmpty) return false
    ignoredSet.forall { result =>
      set.forall { otherResult =>
        if (!ScEquivalenceUtil.smartEquivalence(result.getActualElement, otherResult.getActualElement)) {
          (result.getActualElement, otherResult.getActualElement) match {
            case (ta: ScTypeAliasDefinition, cls: PsiClass) => ta.isExactAliasFor(cls)
            case (cls: PsiClass, ta: ScTypeAliasDefinition) => ta.isExactAliasFor(cls)
            case _ => false
          }
        } else true
      }
    }
  }

  protected def restartFromHistory(): Unit = {
    candidatesSet.clear()
    ignoredSet.clear()
    levelQualifiedNamesSet.clear()
    qualifiedNamesSet.clear()
    levelSet.clear()
    fromHistory = true
    try {
      history.foreach {
        case ChangedLevel => changedLevel
        case AddResult(results) => addResults(results)
      }
    }
    finally fromHistory = false
  }

  def isUpdateHistory: Boolean = false

  protected def addChangedLevelToHistory(): Unit = {
    if (isUpdateHistory && !fromHistory && !history.lastOption.contains(ChangedLevel)) history += ChangedLevel
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
  protected def isSpecialResult(result: ScalaResolveResult): Boolean = {
    val importsUsed = result.importsUsed.toSeq
    if (importsUsed.length == 1) {
      val importExpr = importsUsed.head match {
        case ImportExprUsed(expr) => expr
        case ImportSelectorUsed(selector) => PsiTreeUtil.getContextOfType(selector, true, classOf[ScImportExpr])
        case ImportWildcardSelectorUsed(expr) => expr
      }
      importExpr.qualifier.bind() match {
        case Some(ScalaResolveResult(p: PsiPackage, _)) => suspiciousPackages.contains(p.getQualifiedName)
        case Some(ScalaResolveResult(o: ScObject, _)) => suspiciousPackages.contains(o.qualifiedName)
        case _ => false
      }
    } else false
  }

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
    if (isUpdateHistory && !fromHistory) history += AddResult(results)
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
      if (!fromHistory && isUpdateHistory && isSpecialResult(result)) {
        results.foreach(ignoredSet.add)
      } else addResults()
    } else {
      if (qualifiedName != null && qualifiedNamesSet.contains(qualifiedName)) {
        return false
      } else {
        if (!fromHistory && isUpdateHistory && isSpecialResult(result)) {
          results.foreach(ignoredSet.add)
        } else {
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