package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.openapi.project.Project

/**
 * Each [[Search.Method]] has its own cache, and one [[Search.Method]] instance is bound to a project
 * by virtue of this class being registered as a project service in scala-plugin-common.xml.
 */

private[declarationRedundancy] final class SearchMethodsWithProjectBoundCache private() {
  val LocalSearchMethods: Seq[Search.Method] = Seq(
    new LocalImplicitSearch(c => c.isOnlyVisibleInLocalFile && c.isImplicit),
    new RefCountHolderSearch(c => c.isOnTheFly && c.isMemberOfUnusedTypeDefinition || (c.isOnlyVisibleInLocalFile && !c.isImplicit)),
    new LocalRefSearch(c => !c.isOnlyVisibleInLocalFile || (!c.isOnTheFly && !c.isImplicit))
  )

  val GlobalSearchMethods: Seq[Search.Method] = Seq(
    new ForeignEnumSearch(c => !c.isOnlyVisibleInLocalFile),
    new TextSearch(c => !c.isMemberOfUnusedTypeDefinition && !c.isOnlyVisibleInLocalFile && !c.isImplicit)
  )
}

object SearchMethodsWithProjectBoundCache {
  def apply(project: Project): SearchMethodsWithProjectBoundCache = project.getService(classOf[SearchMethodsWithProjectBoundCache])
}
