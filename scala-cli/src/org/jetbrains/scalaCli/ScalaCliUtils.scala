package org.jetbrains.scalaCli

import com.intellij.ide.wizard.CommitStepException
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.project.BspExternalSystemUtil

import java.io.File
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Success, Try}

object ScalaCliUtils {

  private val BspServerName = "scala-cli"

  def isBspScalaCliProject(project: Project): Boolean = {
    val projectData = BspExternalSystemUtil.getBspProjectData(project)
    projectData.exists(_.serverDisplayName == BspServerName)
  }

  def throwExceptionIfScalaCliNotInstalled(workspace: File): Unit = {
    val command = "scala-cli version"
    val work = Try(Process(command, workspace)! ProcessLogger(_ => (), _ => ()))
    work.ifNonZero(throw new CommitStepException(ScalaCliBundle.message("scala.cli.not.installed")))
  }

  implicit class TryIntOps(workResult: Try[Int]) {
    def ifNonZero[U](onFailureAction: => U): Unit =
      workResult match {
        case Success(0) =>
        case _ => onFailureAction
      }
  }
