package org.jetbrains.plugins.scala.projectHighlighting.base

import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike

abstract class SbtProjectHighlightingTestBase
  extends ScalaProjectHighlightingTestBase
    with SbtExternalSystemImportingTestLike
