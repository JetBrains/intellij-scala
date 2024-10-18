package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.{assertCompilingScalaSources, assertNoErrorsOrWarnings}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
@RunWithJdkVersions(Array(
  TestJdkVersion.JDK_1_8,
  TestJdkVersion.JDK_11,
  TestJdkVersion.JDK_17
))
@Category(Array(classOf[CompilationTests]))
class MultipleClassesInOneFileTest extends ScalaCompilerTestBase {

  override protected def buildProcessJdk: Sdk = CompileServerLauncher.defaultSdk(getProject)

  override protected val incrementalityType: IncrementalityType = IncrementalityType.SBT

  def testRemoveOneClassFileAndCompileAgain(): Unit = {
    addFileToProjectSources("src/main/scala/foo.scala",
      """class Foo
        |class Bar
        |class Baz
        |""".stripMargin)

    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertCompilingScalaSources(messages1, 1)

    val classFileNames = List("Foo", "Bar", "Baz")

    val firstClassFiles = classFileNames.map(findClassFile)
    val firstTimestamps = firstClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    removeFile(firstClassFiles(1)) // remove Bar.class

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
    assertCompilingScalaSources(messages2, 1)

    val secondClassFiles = classFileNames.map(findClassFile)
    val secondTimestamps = secondClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    val recompiled = firstTimestamps.zip(secondTimestamps).forall { case (x, y) => x < y }
    assertTrue("Not all source files were recompiled", recompiled)
  }

  private def removeFile(path: Path): Unit = {
    val virtualFile = VfsUtil.findFileByIoFile(path.toFile, true)
    inWriteAction {
      virtualFile.delete(null)
    }
  }
}
