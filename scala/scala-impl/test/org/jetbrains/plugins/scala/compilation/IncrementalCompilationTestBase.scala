package org.jetbrains.plugins.scala.compilation

import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_12, Scala_2_13, Scala_3_0, ScalacTests}
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.project.IncrementalityType
import org.junit.Assert.assertThat
import org.hamcrest.CoreMatchers.equalTo
import org.jetbrains.plugins.scala.util.matchers.HamcrestMatchers.everyValueGreaterThanIn
import org.junit.experimental.categories.Category

@Category(Array(classOf[ScalacTests]))
abstract class IncrementalCompilationTestBase(scalaVersion: ScalaVersion,
                                              override protected val incrementalityType: IncrementalityType,
                                              override protected val useCompileServer: Boolean = false)
  extends ScalaCompilerTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == scalaVersion

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
    refreshVfs()
    val Seq(firstTsAfter, secondTsAfter, thirdTsAfter) = sources.map(_.targetTimestamps)

    assertThat("First not recompiled", firstTsAfter, everyValueGreaterThanIn(firstTsBefore))
    assertThat("First not recompiled", secondTsAfter, everyValueGreaterThanIn(secondTsBefore))
    assertThat("Third recompiled", thirdTsAfter, equalTo(thirdTsBefore))
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
    refreshVfs()

    val actualTargetFileNames = targetFileNames
    val expectedTargetFileNames = all.flatMap(_.expectedTargetFileNames).toSet
    assertThat(actualTargetFileNames, equalTo(expectedTargetFileNames))
  }

  private def initBuildProject(sourceFiles: SourceFile*): Seq[SourceFile] = {
    val result = sourceFiles.toSeq
    compiler.rebuild().assertNoProblems()

    val actualTargetFileNames = targetFileNames
    val expectedTargetFileNames = result.flatMap(_.expectedTargetFileNames).toSet
    assertThat("Failed initial compilation",
      actualTargetFileNames, equalTo(expectedTargetFileNames)
    )
    result
  }

  private def refreshVfs(): Unit =
    VfsUtil.markDirtyAndRefresh(false, true, true, targetDir)

  private def targetDir: VirtualFile =
    CompilerModuleExtension.getInstance(getModule).getCompilerOutputPath

  private def targetFileNames: Set[String] =
    targetDir.getChildren.map(_.getName).toSet

  private class SourceFile private(name: String)
                                  (implicit version: ScalaVersion) {

    private var classes: Set[String] = Set.empty

    def this(name: String, classes: Set[String], code: String) = {
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

    def expectedTargetFileNames: Set[String] = {
      val suffixes = version match {
        case Scala_3_0 => Set("class", "tasty")
        case _ => Set("class")
      }
      for {
        suffix <- suffixes
        targetName <- classes
      } yield s"$targetName.$suffix"
    }

    private def targetFiles: Set[VirtualFile] = {
      val targetFileNames = expectedTargetFileNames
      targetDir.getChildren.filter { targetFile =>
        targetFileNames contains targetFile.getName
      }.toSet
    }

    def targetTimestamps: Map[String, Long] =
      targetFiles.map { targetFile =>
        targetFile.getName -> targetFile.getTimeStamp
      }.toMap
  }
}

// 2.12
class IncrementalIdeaCompilationTest_2_12
  extends IncrementalCompilationTestBase(Scala_2_12, IncrementalityType.IDEA)

class IncrementalSbtCompilationTest_2_12
  extends IncrementalCompilationTestBase(Scala_2_12, IncrementalityType.SBT)

class IncrementalServerCompilationTest_2_12
  extends IncrementalCompilationTestBase(Scala_2_12, IncrementalityType.IDEA, useCompileServer = true)

// 2.13
class IncrementalIdeaCompilationTest_2_13
  extends IncrementalCompilationTestBase(Scala_2_13, IncrementalityType.IDEA)

class IncrementalSbtCompilationTest_2_13
  extends IncrementalCompilationTestBase(Scala_2_13, IncrementalityType.SBT)

class IncrementalServerCompilationTest_2_13
  extends IncrementalCompilationTestBase(Scala_2_13, IncrementalityType.IDEA, useCompileServer = true)

// 3.0
class IncrementalIdeaCompilationTest_3_0
  extends IncrementalCompilationTestBase(Scala_3_0, IncrementalityType.IDEA)

class IncrementalSbtCompilationTest_3_0
  extends IncrementalCompilationTestBase(Scala_3_0, IncrementalityType.SBT)

class IncrementalServerCompilationTest_3_0
  extends IncrementalCompilationTestBase(Scala_3_0, IncrementalityType.IDEA, useCompileServer = true)
