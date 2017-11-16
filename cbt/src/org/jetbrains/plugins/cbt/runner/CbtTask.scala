package org.jetbrains.plugins.cbt.runner

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt._

case class CbtTask(task: String,
                   useDirect: Boolean,
                   project: Project,
                   taskArguments: Seq[String] = Seq.empty,
                   cbtOptions: Seq[String] = Seq.empty,
                   moduleOpt: Option[Module] = None,
                   listenerOpt: Option[CbtProcessListener] = None,
                   filterOpt: Option[CbtOutputFilter] = None,
                   nameOpt: Option[String] = None,
                   directoryOpt: Option[String] = None) {
  final def workingDir: String =
    directoryOpt
      .orElse(moduleOpt.map(_.baseDir))
      .getOrElse(project.getBasePath)

  final def name: String =
    nameOpt.getOrElse(task)

  def appendListener(other: CbtProcessListener): CbtTask =
    this.copy(
      listenerOpt = listenerOpt match {
        case Some(listener) => Some(listener.append(other))
        case None => Some(other)
      }
    )

  override def toString: String = name
}
