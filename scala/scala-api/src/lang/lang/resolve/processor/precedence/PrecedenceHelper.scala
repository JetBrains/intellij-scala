package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

import com.intellij.psi.PsiElement
import com.intellij.util.containers.SmartHashSet
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import java.util

trait PrecedenceHelper {

  protected def getPlace: PsiElement

  protected val precedenceTypes: PrecedenceTypes = PrecedenceTypes.forElement(getPlace)
  protected lazy val placePackageName: String = Option(ScalaPsiUtil.getPlacePackageName(getPlace)).getOrElse("")

  protected abstract class NameUniquenessStrategy extends Hash.Strategy[ScalaResolveResult] {
    def isValid(result: ScalaResolveResult): Boolean = true

    override def hashCode(result: ScalaResolveResult): Int = result.nameInScope.hashCode

    override def equals(left: ScalaResolveResult, right: ScalaResolveResult): Boolean =
      if (left == null && right == null) true
      else if (left == null || right == null) false
      else left.nameInScope == right.nameInScope
  }

  protected val holder: TopPrecedenceHolder

  protected def nameUniquenessStrategy: NameUniquenessStrategy

  private class UniqueNamesSet extends ObjectOpenCustomHashSet[ScalaResolveResult](nameUniquenessStrategy) {

    override def add(result: ScalaResolveResult): Boolean =
      if (nameUniquenessStrategy.isValid(result)) super.add(result)
      else false

    override def contains(obj: scala.Any): Boolean = obj match {
      case result: ScalaResolveResult if nameUniquenessStrategy.isValid(result) =>
        super.contains(result)
      case _ => false
    }
  }

  protected val levelSet           : util.Set[ScalaResolveResult] = new SmartHashSet()
  protected val uniqueNamesSet     : util.Set[ScalaResolveResult] = new UniqueNamesSet
  protected val levelUniqueNamesSet: util.Set[ScalaResolveResult] = new UniqueNamesSet

  protected def isCheckForEqualPrecedence = true

  protected def clearLevelQualifiedSet(result: ScalaResolveResult): Unit = {
    levelUniqueNamesSet.clear()
  }

  protected def getLevelSet(result: ScalaResolveResult): util.Set[ScalaResolveResult] = levelSet

  /**
    * Do not add ResolveResults through candidatesSet. It may break precedence. Use this method instead.
    */
  protected def addResult(result: ScalaResolveResult): Boolean = addResults(Seq(result))

  protected def addResults(results: Iterable[ScalaResolveResult]): Boolean = {
    if (results.isEmpty)
      return true

    val result: ScalaResolveResult = results.head

    lazy val levelSet = getLevelSet(result)

    def addResults(): Unit = {
      levelUniqueNamesSet.add(result)
      val iterator = results.iterator
      while (iterator.hasNext) {
        levelSet.add(iterator.next())
      }
    }

    val currentPrecedence = precedence(result)
    val topPrecedence = holder(result)

    if (currentPrecedence < topPrecedence)
      false
    else if (currentPrecedence == topPrecedence && levelSet.isEmpty)
      false
    else if (currentPrecedence == topPrecedence) {
      if (isCheckForEqualPrecedence &&
        (levelUniqueNamesSet.contains(result) || uniqueNamesSet.contains(result))) {
        false
      }
      else if (uniqueNamesSet.contains(result))
        false
      else {
        addResults()
        true
      }
    }
    else if (uniqueNamesSet.contains(result)) {
      false
    } else {
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
      true
    }
  }

  protected def precedence(result: ScalaResolveResult): Int =
    if (result.prefixCompletion) precedenceTypes.PREFIX_COMPLETION
    else                         result.getPrecedence(getPlace, placePackageName, precedenceTypes)
}