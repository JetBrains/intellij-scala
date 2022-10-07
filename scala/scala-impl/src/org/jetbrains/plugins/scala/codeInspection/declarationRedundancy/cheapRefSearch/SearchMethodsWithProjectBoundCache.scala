package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isImplicit, isOnlyVisibleInLocalFile}

/**
 * A cheap reference searcher, suitable for on-the-fly inspections.
 *
 * TODO -- Rewrite this doc if we end up merging this
 */

private[declarationRedundancy] final class SearchMethodsWithProjectBoundCache private() {
  val LocalSearchMethods: Seq[Search.Method] = Seq(
    new LocalImplicitSearch(c => isOnlyVisibleInLocalFile(c.element) && isImplicit(c.element)),
    new RefCountHolderSearch(c => c.isOnTheFly && isOnlyVisibleInLocalFile(c.element) && !isImplicit(c.element)),
    new LocalRefSearch(c => !isOnlyVisibleInLocalFile(c.element) || (!c.isOnTheFly && !isImplicit(c.element)))
  )

  val GlobalSearchMethods: Seq[Search.Method] = Seq(
    new ForeignEnumSearch(c => !isOnlyVisibleInLocalFile(c.element)),
    new TextSearch(c => !isOnlyVisibleInLocalFile(c.element) && !isImplicit(c.element))
  )
}

object SearchMethodsWithProjectBoundCache {
  def apply(project: Project): SearchMethodsWithProjectBoundCache = project.getService(classOf[SearchMethodsWithProjectBoundCache])
}
