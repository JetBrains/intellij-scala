package org.jetbrains.sbt.internal

import com.intellij.psi.PsiFile
import com.intellij.task.ProjectTaskRunner
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.shell.SbtProjectTaskRunner

//noinspection ApiStatus
private object InternalDynamicLinkerImpl extends InternalDynamicLinker {
  override def isSbtFile(file: PsiFile): Boolean = file.isInstanceOf[SbtFileImpl]

  override def isSbtProjectTaskRunner(runner: ProjectTaskRunner): Boolean = runner.isInstanceOf[SbtProjectTaskRunner]
}
