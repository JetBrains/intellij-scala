package org.jetbrains.plugins.scala.compiler.highlighting

import org.jetbrains.plugins.scala.ScalaVersion

class CompilerDiagnosticsTest_3_RC extends CompilerDiagnosticsTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}
