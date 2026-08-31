package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.matchers.HamcrestMatchers.everyValueGreaterThanIn
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsJUnit4Runner, RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc, ScalaVersion}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import java.nio.file.{Files, Path}

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithJdkVersions(Array(TestJdkVersion.JDK_1_8, TestJdkVersion.JDK_11, TestJdkVersion.JDK_17))
abstract class IncrementalCompilationTestBase(
  override protected val incrementalityType: IncrementalityType,
  override protected val useCompileServer: Boolean
) extends ScalaCompilerTestBase {

  @Test
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

  @Test
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

  @Test
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

  protected def initBuildProject(sourceFiles: SourceFile*): Seq[SourceFile] =
    initBuildProject(sourceFiles.toSeq, allowWarnings = false)

  protected def initBuildProjectAllowWarnings(sourceFiles: SourceFile*): Seq[SourceFile] =
    initBuildProject(sourceFiles.toSeq, allowWarnings = true)

  private def initBuildProject(sourceFiles: Seq[SourceFile], allowWarnings: Boolean): Seq[SourceFile] = {
    compiler.rebuild().assertNoProblems(allowWarnings)

    val actualTargetFileNames = targetFileNames
    val expectedTargetFileNames = sourceFiles.flatMap(_.expectedTargetFileNames).toSet
    assertThat("Failed initial compilation",
      actualTargetFileNames, equalTo(expectedTargetFileNames)
    )
    sourceFiles
  }

  private def targetDir: Path =
    CompilerModuleExtension.getInstance(getModule).getCompilerOutputPath.toNioPath

  private def targetFileNames: Set[String] =
    targetDir.children().map(_.getFileName.toString).toSet

  protected def classFileNames(className: String)
                              (implicit version: ScalaVersion): Set[String] = {
    val suffixes =
      if (version.isScala3) Set("class", "tasty")
      else Set("class")
    suffixes.map(suffix => s"$className.$suffix")
  }

  protected class SourceFile private(name: String)
                                    (implicit version: ScalaVersion) {

    private var classes: Set[String] = Set.empty

    def this(name: String, classes: Set[String], code: String)
            (implicit version: ScalaVersion) = {
      this(name)
      writeCode(classes, code)
    }

    def writeCode(classes: Set[String], code: String): Unit = {
      addFileToProjectSources(sourceFileName, code)
      this.classes = classes
    }

    def removeSourceFile(): Unit = {
      sourceFile.foreach(VfsTestUtil.deleteFile)
      this.classes = Set.empty
    }

    private def sourceFileName: String =
      s"$name.scala"

    private def sourceFile: Option[VirtualFile] =
      Option(getSourceRootDir.findChild(sourceFileName))

    def expectedTargetFileNames: Set[String] =
      classes.flatMap(classFileNames)

    private def targetFiles: Set[Path] = {
      val targetFileNames = expectedTargetFileNames
      targetDir.children().filter(p => targetFileNames.contains(p.getFileName.toString)).toSet
    }

    def targetTimestamps: Map[String, Long] =
      targetFiles.map { targetFile =>
        targetFile.getFileName.toString -> Files.getLastModifiedTime(targetFile).toInstant.toEpochMilli
      }.toMap
  }
}

// IDEA incremental compiler running in the Scala Compile Server

@Category(Array(classOf[CompilationTests_IDEA]))
abstract class IncrementalIdeaOnServerCompilationTest
  extends IncrementalCompilationTestBase(IncrementalityType.IDEA, useCompileServer = true)

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_10_6,
  TestScalaVersion.Scala_2_10,
  TestScalaVersion.Scala_2_11_0,
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12_0
))
class IncrementalIdeaOnServerCompilationTest_LegacyScalaVersions extends IncrementalIdeaOnServerCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
class IncrementalIdeaOnServerCompilationTest_Scala_2 extends IncrementalIdeaOnServerCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1,
  TestScalaVersion.Scala_3_2
))
class IncrementalIdeaOnServerCompilationTest_Scala_3_Pre_LTS extends IncrementalIdeaOnServerCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_3,
  TestScalaVersion.Scala_3_Latest_RC
))
class IncrementalIdeaOnServerCompilationTest_Scala_3_LTS extends IncrementalIdeaOnServerCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_4,
  TestScalaVersion.Scala_3_5,
  TestScalaVersion.Scala_3_6,
  TestScalaVersion.Scala_3_7
))
class IncrementalIdeaOnServerCompilationTest_Scala_3_Post_LTS extends IncrementalIdeaOnServerCompilationTest

@Category(Array(classOf[CompilationTests_IDEA]))
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_8,
  TestScalaVersion.Scala_3_9,
  TestScalaVersion.Scala_3_Next_RC
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
class IncrementalIdeaOnServerCompilationTest_Scala_3_Next_RC extends IncrementalIdeaOnServerCompilationTest

// IDEA incremental compiler running in the JPS build process

@Category(Array(classOf[CompilationTests_IDEA]))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
abstract class IncrementalIdeaCompilationTest
  extends IncrementalCompilationTestBase(IncrementalityType.IDEA, useCompileServer = false)

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_10_6,
  TestScalaVersion.Scala_2_10,
  TestScalaVersion.Scala_2_11_0,
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12_0
))
class IncrementalIdeaCompilationTest_LegacyScalaVersions extends IncrementalIdeaCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
class IncrementalIdeaCompilationTest_Scala_2 extends IncrementalIdeaCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1,
  TestScalaVersion.Scala_3_2
))
class IncrementalIdeaCompilationTest_Scala_3_Pre_LTS extends IncrementalIdeaCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_3,
  TestScalaVersion.Scala_3_Latest_RC
))
class IncrementalIdeaCompilationTest_Scala_3_LTS extends IncrementalIdeaCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_4,
  TestScalaVersion.Scala_3_5,
  TestScalaVersion.Scala_3_6,
  TestScalaVersion.Scala_3_7
))
class IncrementalIdeaCompilationTest_Scala_3_Post_LTS extends IncrementalIdeaCompilationTest

@Category(Array(classOf[CompilationTests_IDEA]))
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_8,
  TestScalaVersion.Scala_3_9,
  TestScalaVersion.Scala_3_Next_RC
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
class IncrementalIdeaCompilationTest_Scala_3_Next_RC extends IncrementalIdeaCompilationTest

// SBT incremental compiler running in the Scala Compile Server

@Category(Array(classOf[CompilationTests_Zinc]))
abstract class IncrementalSbtOnServerCompilationTest
  extends IncrementalCompilationTestBase(IncrementalityType.SBT, useCompileServer = true) {

  @Test
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

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_10_6,
  TestScalaVersion.Scala_2_10,
  TestScalaVersion.Scala_2_11_0,
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12_0
))
class IncrementalSbtOnServerCompilationTest_LegacyScalaVersions extends IncrementalSbtOnServerCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
class IncrementalSbtOnServerCompilationTest_Scala_2 extends IncrementalSbtOnServerCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1,
  TestScalaVersion.Scala_3_2
))
class IncrementalSbtOnServerCompilationTest_Scala_3_Pre_LTS extends IncrementalSbtOnServerCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_3,
  TestScalaVersion.Scala_3_Latest_RC
))
class IncrementalSbtOnServerCompilationTest_Scala_3_LTS extends IncrementalSbtOnServerCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_4,
  TestScalaVersion.Scala_3_5,
  TestScalaVersion.Scala_3_6,
  TestScalaVersion.Scala_3_7
))
class IncrementalSbtOnServerCompilationTest_Scala_3_Post_LTS extends IncrementalSbtOnServerCompilationTest

@Category(Array(classOf[CompilationTests_IDEA]))
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_8,
  TestScalaVersion.Scala_3_9,
  TestScalaVersion.Scala_3_Next_RC
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
class IncrementalSbtOnServerCompilationTest_Scala_3_Next_RC extends IncrementalSbtOnServerCompilationTest

// SBT incremental compiler running in the JPS build process

@Category(Array(classOf[CompilationTests_Zinc]))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
abstract class IncrementalSbtCompilationTest extends IncrementalSbtOnServerCompilationTest {
  override protected val useCompileServer: Boolean = false
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_10_6,
  TestScalaVersion.Scala_2_10,
  TestScalaVersion.Scala_2_11_0,
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12_0
))
class IncrementalSbtCompilationTest_LegacyScalaVersions extends IncrementalSbtCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
class IncrementalSbtCompilationTest_Scala_2 extends IncrementalSbtCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1,
  TestScalaVersion.Scala_3_2
))
class IncrementalSbtCompilationTest_Scala_3_Pre_LTS extends IncrementalSbtCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_3,
  TestScalaVersion.Scala_3_Latest_RC
))
class IncrementalSbtCompilationTest_Scala_3_LTS extends IncrementalSbtCompilationTest

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_4,
  TestScalaVersion.Scala_3_5,
  TestScalaVersion.Scala_3_6,
  TestScalaVersion.Scala_3_7
))
class IncrementalSbtCompilationTest_Scala_3_Post_LTS extends IncrementalSbtCompilationTest

@Category(Array(classOf[CompilationTests_IDEA]))
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_8,
  TestScalaVersion.Scala_3_9,
  TestScalaVersion.Scala_3_Next_RC
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_17))
class IncrementalSbtCompilationTest_Scala_3_Next_RC extends IncrementalSbtCompilationTest
