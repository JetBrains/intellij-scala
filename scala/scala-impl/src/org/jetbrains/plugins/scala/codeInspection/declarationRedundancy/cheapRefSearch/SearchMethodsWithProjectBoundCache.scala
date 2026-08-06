package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Each [[Search.Method]] has its own cache, and one [[Search.Method]] instance is bound to a project
 * by virtue of this class being adorned with `@Service(Array(Service.Level.PROJECT))`.
 */
@Service(Array(Service.Level.PROJECT))
private[declarationRedundancy] final class SearchMethodsWithProjectBoundCache private(project: Project) {
  val LocalSearchMethods: Seq[Search.Method] = Seq(
    new LocalImplicitSearch(c => c.isOnlyVisibleInLocalFile && c.isImplicit),
    new RefCountHolderSearch(c => c.isOnTheFly && (c.isMemberOfUnusedTypeDefinition || (c.isOnlyVisibleInLocalFile && !c.isImplicit))),
    new LocalRefSearch(c => !c.isMemberOfUnusedTypeDefinition && (!c.isOnlyVisibleInLocalFile || (!c.isOnTheFly && !c.isImplicit)))
  )

  val GlobalSearchMethods: Seq[Search.Method] = Seq(
    new ForeignEnumSearch(c => !c.isOnlyVisibleInLocalFile),
    new TextSearch(c => !c.isMemberOfUnusedTypeDefinition && !c.isOnlyVisibleInLocalFile && !c.isImplicit, project)
  )

  // Any Scala class maybe implements an extension point. Note that such implementations may be private, hence
  // this search method is only used in unused-declaration inspection and not in can-be-private.
  val IJExtensionPointImplementationSearch = new IJExtensionPointImplementationSearch(_.isScalaClass)
}

object SearchMethodsWithProjectBoundCache {
  def apply(project: Project): SearchMethodsWithProjectBoundCache = project.getService(classOf[SearchMethodsWithProjectBoundCache])
}
