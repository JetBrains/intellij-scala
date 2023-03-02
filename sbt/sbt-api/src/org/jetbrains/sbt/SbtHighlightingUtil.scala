package org.jetbrains.sbt

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.TestOnly

object SbtHighlightingUtil {

  //See `org.jetbrains.sbt.codeInsight.daemon.SbtProblemHighlightFilter`
  //NOTE: this utility is placed in `sbt-api`, not in companion object of `SbtProblemHighlightFilter`
  //to be able to use it outside `sbt-impl` module
  private val HighlightSbtFilesOutsideBuildModuleKey: Key[java.lang.Boolean.TRUE.type] = Key.create("HighlightSbtFilesOutsideBuildModule")

  @TestOnly
  def enableHighlightingOutsideBuildModule(project: Project): Unit =
    project.putUserData(HighlightSbtFilesOutsideBuildModuleKey, java.lang.Boolean.TRUE: java.lang.Boolean.TRUE.type)

  def isHighlightingOutsideBuildModuleEnabled(project: Project): Boolean =
    project.getUserData(HighlightSbtFilesOutsideBuildModuleKey) != null
}
