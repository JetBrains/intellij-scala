package org.jetbrains.plugins.scala.compiler.oneFile

import java.util.concurrent.TimeUnit

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.Metering._

class CompileSelectedScalaFileAction
  extends AnAction {

  private implicit val scaleConfig: ScaleConfig =
    ScaleConfig(3, TimeUnit.SECONDS)

  private implicit val meteringHandler: MeteringHandler =
    (time: Double, unit: TimeUnit) => println(s"Elapsed time: $time $unit")

  override def actionPerformed(event: AnActionEvent): Unit = metered {
    val result = for {
      project <- Option(event.getProject)
      sourceFile <- project.selectedDocument
    } yield OneFileCompiler.compile(project, sourceFile)
    println(s"Result: $result")
  }
}
