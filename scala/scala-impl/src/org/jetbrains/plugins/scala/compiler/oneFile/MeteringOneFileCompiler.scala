package org.jetbrains.plugins.scala.compiler.oneFile
import java.util.concurrent.TimeUnit

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.util.Metering._

trait MeteringOneFileCompiler
  extends OneFileCompiler {

  private implicit val scaleConfig: ScaleConfig =
    ScaleConfig(3, TimeUnit.SECONDS)

  protected implicit def meteringHandler: MeteringHandler

  abstract override def compile(project: Project, source: Document): Seq[Client.ClientMsg] = metered {
    super.compile(project, source)
  }
}
