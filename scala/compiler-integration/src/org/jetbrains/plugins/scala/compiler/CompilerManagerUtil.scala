package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project

import java.nio.file.{Files, Path}

object CompilerManagerUtil {

  /**
   * A [[java.nio.file.Path]] implementation of
   * [[com.intellij.openapi.compiler.CompilerManager#getJavacCompilerWorkingDir()]].
   *
   * @see [[com.intellij.compiler.CompilerManagerImpl#getJavacCompilerWorkingDir()]]
   */
  def javacCompilerWorkingDir(project: Project): Path = {
    val projectBuildDir = BuildManager.getInstance().getProjectSystemDir(project)
    Files.createDirectories(projectBuildDir)
    projectBuildDir
  }
}
