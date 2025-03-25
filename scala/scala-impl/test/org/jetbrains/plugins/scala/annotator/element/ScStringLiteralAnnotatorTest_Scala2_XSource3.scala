package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.ScalaVersion

class ScStringLiteralAnnotatorTest_Scala2_XSource3 extends TestCase

object ScStringLiteralAnnotatorTest_Scala2_XSource3 {
  final def suite: Test = new ScStringLiteralAnnotatorTestBase("/annotator/string_literals/scala2_with_xsource3/") {
    override def supportedInScalaVersion(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

    override def setUp(project: Project): Unit = {
      super.setUp(project)

      val module = ModuleManager.getInstance(project).getModules()(0)
      addCompilerOptions(module, Seq("-Xsource:3"))
    }
  }
}
