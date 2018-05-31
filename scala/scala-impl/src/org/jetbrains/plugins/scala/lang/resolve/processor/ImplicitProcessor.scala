package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import java.util.{Map => JMap, Set => JSet}

import gnu.trove.{THashMap, THashSet}
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.Set

/**
  * @author Alexander Podkhalyuzin
  */

/**
  * This class mark processor that only implicit object important among all PsiClasses
  */
abstract class ImplicitProcessor(kinds: Set[Value], withoutPrecedence: Boolean)
                                (implicit projectContext: ProjectContext) extends BaseProcessor(kinds) with SubstitutablePrecedenceHelper {

  override protected def nameUniquenessStrategy: NameUniquenessStrategy = NameUniquenessStrategy.Implicits

  override protected val holder: TopPrecedenceHolder = new MappedTopPrecedenceHolder(nameUniquenessStrategy)

  private[this] val levelMap: JMap[ScalaResolveResult, JSet[ScalaResolveResult]] =
    new THashMap[ScalaResolveResult, JSet[ScalaResolveResult]](nameUniquenessStrategy)

  override protected def clearLevelQualifiedSet(result: ScalaResolveResult) {
    //optimisation, do nothing
  }

  override protected def getLevelSet(result: ScalaResolveResult): JSet[ScalaResolveResult] = {
    var levelSet = levelMap.get(result)
    if (levelSet == null) {
      levelSet = new THashSet[ScalaResolveResult]()
      levelMap.put(result, levelSet)
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
    uniqueNamesSet.addAll(levelUniqueNamesSet)
    levelMap.clear()
    levelUniqueNamesSet.clear()
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

  override protected def isCheckForEqualPrecedence = false

  override def isImplicitProcessor: Boolean = true
}