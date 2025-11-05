package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.{assertCompilingScalaSources, assertNoErrorsOrWarnings}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsJUnit4Runner, RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters._

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_1_8, TestJdkVersion.JDK_11, TestJdkVersion.JDK_17))
abstract class EncodingCompilationTestBase(override val incrementalityType: IncrementalityType) extends ScalaCompilerTestBase {

  @Test
  def testEncoding1(): Unit = {
    runEncodingTest(Seq("-encoding", "UTF-8"))
  }

  @Test
  def testEncoding2(): Unit = {
    runEncodingTest(Seq("--encoding", "UTF-8"))
  }

  @Test
  def testEncoding3(): Unit = {
    runEncodingTest(Seq("-encoding:UTF-8"))
  }

  @Test
  def testEncoding4(): Unit = {
    runEncodingTest(Seq("--encoding:UTF-8"))
  }

  private def runEncodingTest(encodingSettings: Seq[String]): Unit = {
    addFileToProjectSources("Foo.scala", "class Foo")
    val profile = ScalaCompilerSettingsProfile.forModule(getModule)
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

@Category(Array(classOf[CompilationTests_Zinc]))
class EncodingCompilationTest_Zinc extends EncodingCompilationTestBase(IncrementalityType.SBT)

@Category(Array(classOf[CompilationTests_IDEA]))
class EncodingCompilationTest_IDEA extends EncodingCompilationTestBase(IncrementalityType.IDEA)
