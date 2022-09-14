package org.jetbrains.sbt.project.settings

import com.intellij.openapi.project.Project

/**
 * Specifies which compiler (e.g. IDEA's own JPS, sbt shell, etc) is used for project builds
 */
sealed trait CompilerMode

object CompilerMode {
  final case object JPS extends CompilerMode
  final case object SBT extends CompilerMode

  def forProject(project: Project): CompilerMode = {
    val sbtSettings  = SbtProjectSettings.forProject(project)
    val usesSbtShell = sbtSettings.exists(_.useSbtShellForBuild)

    if (usesSbtShell) SBT
    else              JPS
  }
}
