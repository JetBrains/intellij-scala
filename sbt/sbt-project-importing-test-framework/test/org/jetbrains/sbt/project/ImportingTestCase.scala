package org.jetbrains.sbt.project

import scala.compiletime.uninitialized

trait ImportingTestCase:
  private var _importMode: ImportMode = uninitialized

  def importMode: ImportMode = _importMode
  def importMode_=(importMode: ImportMode): Unit =
    _importMode = importMode
