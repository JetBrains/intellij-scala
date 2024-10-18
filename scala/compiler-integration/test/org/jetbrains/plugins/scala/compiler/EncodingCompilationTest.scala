package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.{assertCompilingScalaSources, assertNoErrorsOrWarnings}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters._

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
@RunWithJdkVersions(Array(
  TestJdkVersion.JDK_1_8,
  TestJdkVersion.JDK_11,
  TestJdkVersion.JDK_17
))
@Category(Array(classOf[CompilationTests]))
abstract class EncodingCompilationTestBase(override val incrementalityType: IncrementalityType) extends ScalaCompilerTestBase {

  def testEncoding1(): Unit = {
    runEncodingTest(Seq("-encoding", "UTF-8"))
  }

  def testEncoding2(): Unit = {
    runEncodingTest(Seq("--encoding", "UTF-8"))
  }

  def testEncoding3(): Unit = {
    runEncodingTest(Seq("-encoding:UTF-8"))
  }

  def testEncoding4(): Unit = {
    runEncodingTest(Seq("--encoding:UTF-8"))
  }

  private def runEncodingTest(encodingSettings: Seq[String]): Unit = {
    addFileToProjectSources("Foo.scala", "class Foo")
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = encodingSettings
    )
    profile.setSettings(newSettings)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    incrementalityType match {
      case IncrementalityType.SBT => assertCompilingScalaSources(messages, 1)
      case IncrementalityType.IDEA =>
    }
  }
}

class EncodingCompilationTest_Zinc extends EncodingCompilationTestBase(IncrementalityType.SBT)

class EncodingCompilationTest_IDEA extends EncodingCompilationTestBase(IncrementalityType.IDEA)
