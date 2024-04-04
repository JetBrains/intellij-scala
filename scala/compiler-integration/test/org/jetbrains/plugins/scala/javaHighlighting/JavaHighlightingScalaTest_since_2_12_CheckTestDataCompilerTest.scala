package org.jetbrains.plugins.scala.javaHighlighting

import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

/** see [[org.jetbrains.plugins.scala.javaHighlighting.JavaHighlightingScalaTest_since_2_12]] */
@Category(Array(classOf[CompilationTests]))
@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_10,
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
  TestScalaVersion.Scala_3_Latest_RC
))
class JavaHighlightingScalaTest_since_2_12_CheckTestDataCompilerTest extends ScalaCompilerTestBase {

  def testTestDataWithPackageObjectsCompiles(): Unit = {
    JavaHighlightingScalaTest_since_2_12.addScalaPackageObjectDefinitions(addFileToProjectSources)

    compiler.make().assertNoProblems()
  }
}