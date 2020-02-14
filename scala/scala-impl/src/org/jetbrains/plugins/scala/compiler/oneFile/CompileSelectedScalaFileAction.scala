package org.jetbrains.plugins.scala.compiler.oneFile

import java.util.concurrent.TimeUnit

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.Metering.MeteringHandler

class CompileSelectedScalaFileAction
  extends AnAction {

  private val compiler: OneFileCompiler = new OneFileCompilerImpl
    with MeteringOneFileCompiler {

    override protected implicit val meteringHandler: MeteringHandler =
      (time: Double, unit: TimeUnit) => println(s"Selected scala file compiled: $time $unit")
  }

  override def actionPerformed(event: AnActionEvent): Unit =
    for {
      project <- Option(event.getProject)
      sourceFile <- project.selectedDocument
    } yield compiler.compile(project, sourceFile)
}
