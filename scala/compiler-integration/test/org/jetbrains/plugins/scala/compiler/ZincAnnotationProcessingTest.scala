package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.vfs.VfsUtil
import junit.framework.TestCase.{assertEquals, assertTrue}
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.{assertCompilingScalaSources, assertNoErrorsOrWarnings}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.{ScalaVersion, SlowTests}
import org.junit.experimental.categories.Category

import java.nio.file.Files
import scala.jdk.CollectionConverters._

@Category(Array(classOf[SlowTests]))
class ZincAnnotationProcessingTest extends ScalaCompilerTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override protected def incrementalityType: IncrementalityType = IncrementalityType.SBT

  override protected def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader("org.jetbrains" % "annotations" % "26.0.1"))

  def testAnnotationProcessing(): Unit = {
    addFileToProjectSources("A.scala",
      """import org.jetbrains.annotations.NotNull
        |
        |class A(@NotNull x: String)
        |""".stripMargin)
    addFileToProjectSources("B.scala", "class B")

    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertCompilingScalaSources(messages1, 2)

    val classFileNames = Seq("A", "B")

    val firstClassFiles = classFileNames.map(findClassFile)
    val firstTimestamps = firstClassFiles.map(Files.getLastModifiedTime(_).toMillis)
    assertTrue("There should be exactly two class files", firstClassFiles.sizeIs == 2)

    inWriteAction {
      VfsUtil.saveText(getSourceRootDir.findChild("B.scala"), "class B { def x = 5 }")
    }

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
    assertCompilingScalaSources(messages2, 1)

    val secondClassFiles = classFileNames.map(findClassFile)
    val secondTimestamps = secondClassFiles.map(Files.getLastModifiedTime(_).toMillis)
    assertTrue("There should be exactly two class files", firstClassFiles.sizeIs == 2)

    (firstTimestamps, secondTimestamps) match {
      case (Seq(aBefore, bBefore), Seq(aAfter, bAfter)) =>
        assertEquals("A.scala was recompiled when it shouldn't have been", aBefore, aAfter)
        assertTrue("B.scala was not recompiled when it should have been", bBefore < bAfter)
    }
  }
}
