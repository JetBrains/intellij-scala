package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import java.util

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
                                (implicit projectContext: ProjectContext) extends BaseProcessor(kinds) with SubstitutablePrecedenceHelper[String] {

  override protected val holder: TopPrecedenceHolder[String] = new TopPrecedenceHolderImpl[String] {

    override def toRepresentation(result: ScalaResolveResult): String = result.nameInScope
  }

  protected val levelMap: util.HashMap[String, util.HashSet[ScalaResolveResult]] = new util.HashMap[String, util.HashSet[ScalaResolveResult]]()

  override protected def clearLevelQualifiedSet(result: ScalaResolveResult) {
    //optimisation, do nothing
  }

  override protected def getLevelSet(result: ScalaResolveResult): util.HashSet[ScalaResolveResult] = {
    val qualifiedName: String = holder.toRepresentation(result)
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

  override protected def isCheckForEqualPrecedence = false

  override def isImplicitProcessor: Boolean = true
}