package org.jetbrains.sbt.shell.optionsWarn

import com.intellij.build.AbstractViewManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.SbtBundle

/**
 * This is responsible for having an extra special tab "sbt shell" in the "Build" tool window.
 *
 * Other known popular tabs are "Sync" and "Build Output".
 * Neither of them seems to be fitting as this represents the "sbt shell startup", not the "Build" or "Sync".
 * Also, with the current APIs it's hard to propagate those entities as sbt shell startup logic doesn't know from which context it's being initiated.
 *
 * For now this view manager has only 1 purpose:<br>
 * Show warning details about unrecognized / malformed SBT options during SBT shell startup initialization.
 * It's the most convenient place to show this data.
 * It's also unified with how it looks when sbt is used during Project Sync when sbt shell is not used.
 *
 * So this is NOT used in default production cases when everything is fine with the setup.
 * It's only shown in the case of issues with some sbt options.
 *
 * @see [[org.jetbrains.sbt.process.options.reporting.SbtOptionsDiagnosticsReporter]]
 * @see [[org.jetbrains.plugins.scala.build.BuildReporter]]
 */
@Service(Array(Service.Level.PROJECT))
private[shell] final class SbtShellBuildViewManager(project: Project) extends AbstractViewManager(project) {
  override protected def getViewName: String =
    SbtBundle.message("sbt.shell.title")
}

private[shell] object SbtShellBuildViewManager {
  def instance(project: Project): SbtShellBuildViewManager =
    project.getService(classOf[SbtShellBuildViewManager])
}
