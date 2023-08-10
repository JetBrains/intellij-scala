package org.jetbrains.plugins.scala.compiler

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.util.matchers.HamcrestMatchers.everyValueGreaterThanIn
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_10_6,
  TestScalaVersion.Scala_2_10,
  TestScalaVersion.Scala_2_11_0,
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12_0,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1,
  TestScalaVersion.Scala_3_2,
  TestScalaVersion.Scala_3_3,
  TestScalaVersion.Scala_3_Latest_RC
))
@Category(Array(classOf[CompilationTests]))
abstract class IncrementalCompilationTestBase(incrementalityType: IncrementalityType, useCompileServer: Boolean)
  extends ScalaCompilationTestBase(incrementalityType, useCompileServer) {

  def testRecompileOnlyAffectedFiles(): Unit = {
    val sources = initBuildProject(
      new SourceFile(
        name = "Fist",
        classes = Set("First"),
        code =
          """
            |class First {
            |  def x = 1
            |}
            |""".stripMargin
      ),
      new SourceFile(
        name = "Second",
        classes = Set("Second"),
        code =
          """
            |class Second extends First {
            |  println(x)
            |}
            |""".stripMargin,
      ),
      new SourceFile(
        name = "Third",
        classes = Set("Third"),
        code =
          """
            |class Third
            |""".stripMargin
      )
    )
    val Seq(firstTsBefore, secondTsBefore, thirdTsBefore) = sources.map(_.targetTimestamps)

    sources.head.writeCode(
      classes = Set("First"),
      code =
        """
          |class First { def x = 1.0 }
          |""".stripMargin,
    )
    compiler.make().assertNoProblems()
    val Seq(firstTsAfter, secondTsAfter, thirdTsAfter) = sources.map(_.targetTimestamps)

    assertThat("First hasn't been recompiled", firstTsAfter, everyValueGreaterThanIn(firstTsBefore))
    assertThat("Second hasn't been recompiled", secondTsAfter, everyValueGreaterThanIn(secondTsBefore))
    assertThat("Third has been recompiled", thirdTsAfter, equalTo(thirdTsBefore))
  }

  def testDeleteOldTargetFiles(): Unit = {
    val all@Seq(first, second) = initBuildProject(
      new SourceFile(
        name = "First",
        classes = Set("First1", "First2"),
        code =
          """
            |class First1
            |class First2
            |""".stripMargin
      ),
      new SourceFile(
        name = "Second",
        classes = Set("Second"),
        code =
          """
            |class Second
            |""".stripMargin
      )
    )

    first.writeCode(
      classes = Set("First1"),
      code =
        """
          |class First1
          |""".stripMargin
    )
    second.removeSourceFile()
    compiler.make().assertNoProblems()

    val actualTargetFileNames = targetFileNames
    val expectedTargetFileNames = all.flatMap(_.expectedTargetFileNames).toSet
    assertThat(actualTargetFileNames, equalTo(expectedTargetFileNames))
  }

  def testDeleteTargetFilesForInvalidSources(): Unit = {
    val all@Seq(first, _) = initBuildProject(
      new SourceFile(
        name = "First",
        classes = Set("First"),
        code =
          """
            |class First
            |""".stripMargin
      ),
      new SourceFile(
        name = "Second",
        classes = Set("Second"),
        code =
          """
            |class Second
            |""".stripMargin
      )
    )

    first.writeCode(
      classes = Set.empty,
      code =
        """
          |clas First1 // should not compile
          |""".stripMargin
    )
    compiler.make()

    val actualTargetFileNames = targetFileNames
    val expectedTargetFileNames = all.flatMap(_.expectedTargetFileNames).toSet
    assertThat(actualTargetFileNames, equalTo(expectedTargetFileNames))
  }
}

@RunWithJdkVersions(Array(
  TestJdkVersion.JDK_1_8,
  TestJdkVersion.JDK_11,
  TestJdkVersion.JDK_17
))
class IncrementalIdeaOnServerCompilationTest
  extends IncrementalCompilationTestBase(IncrementalityType.IDEA, useCompileServer = true)

class IncrementalIdeaCompilationTest
  extends IncrementalCompilationTestBase(IncrementalityType.IDEA, useCompileServer = false)

class IncrementalSbtCompilationTest
  extends IncrementalCompilationTestBase(IncrementalityType.SBT, useCompileServer = false) {

  def testRecompileOnlyAffectedFilesScalaSpecific(): Unit = {
    val sources = initBuildProjectAllowWarnings(
      new SourceFile(
        name = "MySealed",
        classes = Set("MySealed", "MyClassA", "MyClassB"),
        code =
          """
            |sealed trait MySealed
            |class MyClassA extends MySealed
            |class MyClassB extends MySealed
            |""".stripMargin
      ),
      new SourceFile(
        name = "MyApp",
        classes = Set("MyApp"),
        code =
          """
            |class MyApp {
            |  (null: MySealed) match {
            |    case _: MyClassA =>
            |  }
            |}
            |""".stripMargin
      )
    )
    val Seq(sealedTsBefore, appTsBefore) = sources.map(_.targetTimestamps)
    sources.head.writeCode(
      classes = Set("MySealed", "MyClassA"),
      code =
        """
          |sealed trait MySealed
          |class MyClassA extends MySealed
          |""".stripMargin
    )
    compiler.make().assertNoProblems()
    val Seq(sealedTsAfter, appTsAfter) = sources.map(_.targetTimestamps)

    val sealedTsBeforeWithoutB = sealedTsBefore -- classFileNames("MyClassB")
    assertThat("Sealed recompiled", sealedTsAfter, everyValueGreaterThanIn(sealedTsBeforeWithoutB))
    assertThat("App recompiled", appTsAfter, everyValueGreaterThanIn(appTsBefore))
  }
}

@RunWithJdkVersions(Array(
  TestJdkVersion.JDK_1_8,
  TestJdkVersion.JDK_11,
  TestJdkVersion.JDK_17
))
class IncrementalSbtOnServerCompilationTest extends IncrementalSbtCompilationTest {
  override protected val useCompileServer: Boolean = true
}
