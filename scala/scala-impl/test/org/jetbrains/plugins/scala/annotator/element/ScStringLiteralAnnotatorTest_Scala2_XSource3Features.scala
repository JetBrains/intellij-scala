package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.module.ModuleManager
import org.jetbrains.plugins.scala.ScalaVersion

import java.nio.file.Path

//NOTE: reusing Scala 3 test data as they Scala 3 and Scala 2 + xSourceFeatures flag have the same semantics
class ScStringLiteralAnnotatorTest_Scala2_XSource3Features extends ScStringLiteralAnnotatorTestBase {
  override protected def relativeTestDataPath: Path = Path.of("annotator", "string_literals", "scala3")

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  override protected def setUp(): Unit = {
    super.setUp()

    val module = ModuleManager.getInstance(project).getModules()(0)
    addCompilerOptions(module, Seq("-Xsource:3", "-Xsource-features:unicode-escapes-raw"))
  }
}
