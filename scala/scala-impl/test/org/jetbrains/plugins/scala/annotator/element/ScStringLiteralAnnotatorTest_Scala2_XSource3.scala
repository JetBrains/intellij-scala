package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.module.ModuleManager
import org.jetbrains.plugins.scala.ScalaVersion

import java.nio.file.Path

class ScStringLiteralAnnotatorTest_Scala2_XSource3 extends ScStringLiteralAnnotatorTestBase {
  override protected def relativeTestDataPath: Path = Path.of("annotator", "string_literals", "scala2_with_xsource3")

  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  override protected def setUp(): Unit = {
    super.setUp()

    val module = ModuleManager.getInstance(project).getModules.head
    addCompilerOptions(module, Seq("-Xsource:3"))
  }
}
