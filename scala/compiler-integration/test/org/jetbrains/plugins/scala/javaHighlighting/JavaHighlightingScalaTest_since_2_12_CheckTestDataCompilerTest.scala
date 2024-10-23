package org.jetbrains.plugins.scala.javaHighlighting

import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.{ScalaVersion, SlowTests}
import org.junit.experimental.categories.Category

class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest_2_10
  extends JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(ScalaVersion.Latest.Scala_2_10)

class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest_2_11
  extends JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(ScalaVersion.Latest.Scala_2_11)

class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest_2_12
  extends JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(ScalaVersion.Latest.Scala_2_12)

class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest_2_13
  extends JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(ScalaVersion.Latest.Scala_2_13)

class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest_3_3
  extends JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(ScalaVersion.Latest.Scala_3_3)

class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest_3_4
  extends JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(ScalaVersion.Latest.Scala_3_4)

class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest_3_5
  extends JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(ScalaVersion.Latest.Scala_3_5)

class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest_3_6
  extends JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(ScalaVersion.Latest.Scala_3_6)

class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest_3_LTS_RC
  extends JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest_3_Next_RC
  extends JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(ScalaVersion.Latest.Scala_3_Next_RC)

/** see [[org.jetbrains.plugins.scala.javaHighlighting.JavaHighlightingScalaTest_since_2_12]] */
@Category(Array(classOf[SlowTests]))
abstract class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTestBase(scalaVersion: ScalaVersion)
  extends ScalaCompilerTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

  def testTestDataWithPackageObjectsCompiles(): Unit = {
    JavaHighlightingScalaTest_since_2_12.addScalaPackageObjectDefinitions(addFileToProjectSources)

    compiler.make().assertNoProblems()
  }
}
