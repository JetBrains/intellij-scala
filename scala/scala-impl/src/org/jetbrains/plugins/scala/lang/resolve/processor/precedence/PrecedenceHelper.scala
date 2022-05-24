package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.util.containers.SmartHashSet
import gnu.trove.{THashSet, TObjectHashingStrategy}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import java.util
import scala.annotation.tailrec

trait PrecedenceHelper {

  import PrecedenceHelper._

  protected def getPlace: PsiElement

  protected val precedenceTypes: PrecedenceTypes = PrecedenceTypes.forElement(getPlace)
  protected lazy val placePackageName: String = getPlacePackage(getPlace)

  protected abstract class NameUniquenessStrategy extends TObjectHashingStrategy[ScalaResolveResult] {

    def isValid(result: ScalaResolveResult): Boolean = true

    override def computeHashCode(result: ScalaResolveResult): Int = result.nameInScope.hashCode

    override def equals(left: ScalaResolveResult, right: ScalaResolveResult): Boolean =
      left.nameInScope == right.nameInScope
  }

  protected val holder: TopPrecedenceHolder

  protected def nameUniquenessStrategy: NameUniquenessStrategy

  private class UniqueNamesSet extends THashSet[ScalaResolveResult](nameUniquenessStrategy) {

    override def add(result: ScalaResolveResult): Boolean =
      if (nameUniquenessStrategy.isValid(result)) super.add(result)
      else false

    override def contains(obj: scala.Any): Boolean = obj match {
      case result: ScalaResolveResult if nameUniquenessStrategy.isValid(result) => super.contains(result)
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
    if (results.isEmpty) return true
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
      return false
    else if (currentPrecedence == topPrecedence && levelSet.isEmpty)
      return false
    else if (currentPrecedence == topPrecedence) {
      if (isCheckForEqualPrecedence &&
        (levelUniqueNamesSet.contains(result) || uniqueNamesSet.contains(result))) {
        return false
      } else if (uniqueNamesSet.contains(result))
        return false
      addResults()
    } else {
      if (uniqueNamesSet.contains(result)) {
        return false
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
      }
    }
    true
  }

  protected def precedence(result: ScalaResolveResult): Int =
    if (result.prefixCompletion) precedenceTypes.PREFIX_COMPLETION
    else                         result.getPrecedence(getPlace, placePackageName, precedenceTypes)
}

object PrecedenceHelper {

  @tailrec
  private def getPlacePackage(place: PsiElement): String = {
    val context = getContextOfType(place, true, classOf[ScPackaging], classOf[ScObject], classOf[ScalaFile])
    context match {
      case pack: ScPackaging                    => pack.fullPackageName
      case obj: ScObject if obj.isPackageObject => obj.qualifiedName
      case obj: ScObject                        => getPlacePackage(obj)
      case null | _: ScalaFile                  => ""
    }
  }
}