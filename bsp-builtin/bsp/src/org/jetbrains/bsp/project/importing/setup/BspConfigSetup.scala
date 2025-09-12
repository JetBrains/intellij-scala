package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}

import scala.util.Try

abstract class BspConfigSetup {
  def cancel(): Unit
  def run(indicator: ProgressIndicator)(implicit reporter: BuildReporter): Try[BuildMessages]
}
