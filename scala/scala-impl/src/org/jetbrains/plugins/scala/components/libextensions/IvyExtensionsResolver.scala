package org.jetbrains.plugins.scala.components.libextensions

import org.apache.ivy.core.report.ResolveReport
import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.{ResolvedDependency, Resolver, stripScalaVersion}

class IvyExtensionsResolver(projectResolvers: Seq[Resolver]) extends DependencyManagerBase {
  override protected val resolvers: Seq[Resolver] = projectResolvers

  override protected val logLevel: Int = org.apache.ivy.util.Message.MSG_ERR

  override protected def processIvyReport(report: ResolveReport): Seq[DependencyManagerBase.ResolvedDependency] = {
    report
      .getAllArtifactsReports
      .filter(r => !artifactBlackList.contains(stripScalaVersion(r.getName)))
      .map(a => ResolvedDependency(artToDep(a.getArtifact.getModuleRevisionId), a.getLocalFile))
  }
}
