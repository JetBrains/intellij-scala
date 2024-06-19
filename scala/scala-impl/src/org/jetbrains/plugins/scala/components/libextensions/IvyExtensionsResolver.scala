package org.jetbrains.plugins.scala.components.libextensions

import com.intellij.openapi.progress.ProgressIndicator
import org.apache.ivy.core.report.ResolveReport
import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.{Resolver, stripScalaVersion}

class IvyExtensionsResolver(
  projectResolvers: Seq[Resolver],
  private val indicator: ProgressIndicator
) extends DependencyManagerBase {

  override protected val resolvers: Seq[Resolver] = projectResolvers

  override protected val logLevel: Int = org.apache.ivy.util.Message.MSG_ERR

  override protected def processIvyReport(report: ResolveReport): Seq[DependencyManagerBase.ResolvedDependency] = {
    report
      .getAllArtifactsReports
      .filter(r => !artifactBlackList.contains(stripScalaVersion(r.getName)))
      .map(artifactReportToResolvedDependency)
      .toSeq
  }

  override protected def progressIndicator: Option[ProgressIndicator] = Some(indicator)

  override def createLogger: ProgressIndicatorLogger = new ProgressIndicatorLogger(indicator)
}
