package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import org.jetbrains.plugins.scala.ScalaVersion

abstract class Scala3UnusedDeclarationInspectionTestBase extends ScalaUnusedDeclarationInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  protected override def setUp(): Unit = {
    super.setUp()
    getInspectionTool.setEnableInScala3(true)
  }
}
