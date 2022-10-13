package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isImplicit, isOnlyVisibleInLocalFile}

/**
 * Each [[Search.Method]] has its own cache, and one [[Search.Method]] instance is bound to a project
 * by virtue of this class being registered as a project service in scala-plugin-common.xml.
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
