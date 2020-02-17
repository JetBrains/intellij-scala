package org.jetbrains.plugins.scala.externalHighlighters.compiler

import java.util.concurrent.TimeUnit

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.util.Metering.{MeteringHandler, ScaleConfig, metered}
import org.jetbrains.plugins.scala.util.Opt

trait MeteringHighlightingCompiler
  extends HighlightingCompiler {

  private implicit val scaleConfig: ScaleConfig =
    ScaleConfig(3, TimeUnit.SECONDS)

  protected implicit def meteringHandler: MeteringHandler

  abstract override def compile(project: Project, source: Document): Opt[Unit] = metered {
    super.compile(project, source)
  }
}
