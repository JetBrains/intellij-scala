package org.jetbrains.bsp.project.importing.setup

import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}

import scala.util.Try

abstract class BspConfigSetup {
  def cancel(): Unit
  def run(implicit reporter: BuildReporter): Try[BuildMessages]
}
