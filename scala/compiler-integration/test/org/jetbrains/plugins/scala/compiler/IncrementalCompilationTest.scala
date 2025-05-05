package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.VfsTestUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.matchers.HamcrestMatchers.everyValueGreaterThanIn
import org.jetbrains.plugins.scala.{CompilationTests, ScalaVersion}
import org.junit.experimental.categories.Category

import java.nio.file.{Files, Path}

@Category(Array(classOf[CompilationTests]))
abstract class IncrementalCompilationTestBase(
  scalaVersion: ScalaVersion,
  override protected val incrementalityType: IncrementalityType,
  override protected val useCompileServer: Boolean
) extends ScalaCompilerTestBase with JdkVersionDiscovery {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

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

// IncrementalIdeaOnServerCompilationTests

abstract class IncrementalIdeaOnServerCompilationTestBase(scalaVersion: ScalaVersion)
  extends IncrementalCompilationTestBase(scalaVersion, IncrementalityType.IDEA, useCompileServer = true)
  
class IncrementalIdeaOnServerCompilationTest_2_10_6 extends IncrementalIdeaOnServerCompilationTestBase(MinorScalaVersions.Scala_2_10_6)

class IncrementalIdeaOnServerCompilationTest_2_10 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_2_10)

class IncrementalIdeaOnServerCompilationTest_2_11_0 extends IncrementalIdeaOnServerCompilationTestBase(MinorScalaVersions.Scala_2_11_0)

class IncrementalIdeaOnServerCompilationTest_2_11 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_2_11)

class IncrementalIdeaOnServerCompilationTest_2_12_0 extends IncrementalIdeaOnServerCompilationTestBase(MinorScalaVersions.Scala_2_12_0)

class IncrementalIdeaOnServerCompilationTest_2_12 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_2_12)

class IncrementalIdeaOnServerCompilationTest_2_13 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_2_13)

class IncrementalIdeaOnServerCompilationTest_3_0 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_0)

class IncrementalIdeaOnServerCompilationTest_3_1 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_1)

class IncrementalIdeaOnServerCompilationTest_3_2 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_2)

class IncrementalIdeaOnServerCompilationTest_3_3 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_3)

class IncrementalIdeaOnServerCompilationTest_3_4 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_4)

class IncrementalIdeaOnServerCompilationTest_3_5 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_5)

class IncrementalIdeaOnServerCompilationTest_3_6 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_6)

class IncrementalIdeaOnServerCompilationTest_3_7 extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_7)

class IncrementalIdeaOnServerCompilationTest_3_LTS_RC extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class IncrementalIdeaOnServerCompilationTest_3_Next_RC extends IncrementalIdeaOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_Next_RC)

// IncrementalIdeaCompilationTests

abstract class IncrementalIdeaCompilationTestBase(scalaVersion: ScalaVersion)
  extends IncrementalCompilationTestBase(scalaVersion, IncrementalityType.IDEA, useCompileServer = false) {
  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_17
}
  
class IncrementalIdeaCompilationTest_2_10_6 extends IncrementalIdeaCompilationTestBase(MinorScalaVersions.Scala_2_10_6)

class IncrementalIdeaCompilationTest_2_10 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_2_10)

class IncrementalIdeaCompilationTest_2_11_0 extends IncrementalIdeaCompilationTestBase(MinorScalaVersions.Scala_2_11_0)

class IncrementalIdeaCompilationTest_2_11 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_2_11)

class IncrementalIdeaCompilationTest_2_12_0 extends IncrementalIdeaCompilationTestBase(MinorScalaVersions.Scala_2_12_0)

class IncrementalIdeaCompilationTest_2_12 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_2_12)

class IncrementalIdeaCompilationTest_2_13 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_2_13)

class IncrementalIdeaCompilationTest_3_0 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_3_0)

class IncrementalIdeaCompilationTest_3_1 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_3_1)

class IncrementalIdeaCompilationTest_3_2 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_3_2)

class IncrementalIdeaCompilationTest_3_3 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_3_3)

class IncrementalIdeaCompilationTest_3_4 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_3_4)

class IncrementalIdeaCompilationTest_3_5 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_3_5)

class IncrementalIdeaCompilationTest_3_6 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_3_6)

class IncrementalIdeaCompilationTest_3_7 extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_3_7)

class IncrementalIdeaCompilationTest_3_LTS_RC extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class IncrementalIdeaCompilationTest_3_Next_RC extends IncrementalIdeaCompilationTestBase(ScalaVersion.Latest.Scala_3_Next_RC)

// IncrementalSbtCompilationTests

abstract class IncrementalSbtOnServerCompilationTestBase(scalaVersion: ScalaVersion)
  extends IncrementalCompilationTestBase(scalaVersion, IncrementalityType.SBT, useCompileServer = true) {

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

class IncrementalSbtOnServerCompilationTest_2_10_6 extends IncrementalSbtOnServerCompilationTestBase(MinorScalaVersions.Scala_2_10_6)

class IncrementalSbtOnServerCompilationTest_2_10 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_2_10)

class IncrementalSbtOnServerCompilationTest_2_11_0 extends IncrementalSbtOnServerCompilationTestBase(MinorScalaVersions.Scala_2_11_0)

class IncrementalSbtOnServerCompilationTest_2_11 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_2_11)

class IncrementalSbtOnServerCompilationTest_2_12_0 extends IncrementalSbtOnServerCompilationTestBase(MinorScalaVersions.Scala_2_12_0)

class IncrementalSbtOnServerCompilationTest_2_12 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_2_12)

class IncrementalSbtOnServerCompilationTest_2_13 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_2_13)

class IncrementalSbtOnServerCompilationTest_3_0 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_0)

class IncrementalSbtOnServerCompilationTest_3_1 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_1)

class IncrementalSbtOnServerCompilationTest_3_2 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_2)

class IncrementalSbtOnServerCompilationTest_3_3 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_3)

class IncrementalSbtOnServerCompilationTest_3_4 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_4)

class IncrementalSbtOnServerCompilationTest_3_5 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_5)

class IncrementalSbtOnServerCompilationTest_3_6 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_6)

class IncrementalSbtOnServerCompilationTest_3_7 extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_7)

class IncrementalSbtOnServerCompilationTest_3_LTS_RC extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class IncrementalSbtOnServerCompilationTest_3_Next_RC extends IncrementalSbtOnServerCompilationTestBase(ScalaVersion.Latest.Scala_3_Next_RC)

// IncrementalSbtCompilationTests
abstract class IncrementalSbtCompilationTestBase(scalaVersion: ScalaVersion)
  extends IncrementalSbtOnServerCompilationTestBase(scalaVersion) {

  override protected val useCompileServer: Boolean = false

  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_17
}

class IncrementalSbtCompilationTest_2_10_6 extends IncrementalSbtCompilationTestBase(MinorScalaVersions.Scala_2_10_6)

class IncrementalSbtCompilationTest_2_10 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_2_10)

class IncrementalSbtCompilationTest_2_11_0 extends IncrementalSbtCompilationTestBase(MinorScalaVersions.Scala_2_11_0)

class IncrementalSbtCompilationTest_2_11 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_2_11)

class IncrementalSbtCompilationTest_2_12_0 extends IncrementalSbtCompilationTestBase(MinorScalaVersions.Scala_2_12_0)

class IncrementalSbtCompilationTest_2_12 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_2_12)

class IncrementalSbtCompilationTest_2_13 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_2_13)

class IncrementalSbtCompilationTest_3_0 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_3_0)

class IncrementalSbtCompilationTest_3_1 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_3_1)

class IncrementalSbtCompilationTest_3_2 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_3_2)

class IncrementalSbtCompilationTest_3_3 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_3_3)

class IncrementalSbtCompilationTest_3_4 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_3_4)

class IncrementalSbtCompilationTest_3_5 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_3_5)

class IncrementalSbtCompilationTest_3_6 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_3_6)

class IncrementalSbtCompilationTest_3_7 extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_3_7)

class IncrementalSbtCompilationTest_3_LTS_RC extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class IncrementalSbtCompilationTest_3_Next_RC extends IncrementalSbtCompilationTestBase(ScalaVersion.Latest.Scala_3_Next_RC)

private object MinorScalaVersions {
  val Scala_2_10_6: ScalaVersion = ScalaVersion.Latest.Scala_2_10.withMinor("6")
  val Scala_2_11_0: ScalaVersion = ScalaVersion.Latest.Scala_2_11.withMinor("0")
  val Scala_2_12_0: ScalaVersion = ScalaVersion.Latest.Scala_2_12.withMinor("0")
}
