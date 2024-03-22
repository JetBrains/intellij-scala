package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import com.intellij.codeInspection.incorrectFormatting.FormattingChangesKt
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaSDKLoader
import org.jetbrains.plugins.scala.extensions.IterableOnceExt
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}
import org.junit.Assert.assertNotNull

import scala.collection.mutable
import scala.util.control.NonFatal

class DetectFormattingChangesTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testSomeRandomScalaDocSpecialCase(): Unit = {
    // It's a stripped version of Option.scala
    configureScalaFromFileText(
      """/**
        | *  {{{
        | *  }}}
        | *
        | *  @define none none
        | *  @define option option
        | */
        |sealed abstract class Option
        |""".stripMargin
    )

    testNoExceptionsInDetectingFormatterChanges(getFile)
  }

  def testScalaLibrarySources(): Unit = {
    val scalaLibrarySourcesRoot = librariesLoaders.findByType[ScalaSDKLoader].get.sourceRoot

    val scalaFiles = findAllScalaFiles(scalaLibrarySourcesRoot).sortBy(_.getPath)

    val fileManager = PsiManager.getInstance(getProject).asInstanceOf[PsiManagerEx].getFileManager

    for ((file, _) <- scalaFiles.zipWithIndex) {
      val psiFile = fileManager.findFile(file)
      try {
        testNoExceptionsInDetectingFormatterChanges(psiFile)
      } catch {
        case NonFatal(e) =>
          throw new AssertionError(s"An error occurred while processing file ${file.getPath}", e)
      }
    }
  }

  private def testNoExceptionsInDetectingFormatterChanges(psiFile: PsiFile): Unit = {
    val changes = FormattingChangesKt.detectFormattingChanges(psiFile)
    assertNotNull(changes)
  }

  private def findAllScalaFiles(sourceRoot: VirtualFile): Seq[VirtualFile] = {
    val allScalaFiles = mutable.ArrayBuffer.empty[VirtualFile]
    VfsUtilCore.processFilesRecursively(
      sourceRoot,
      (vFile: VirtualFile) => {
        if (vFile.getFileType == ScalaFileType.INSTANCE) {
          allScalaFiles += vFile
        }
        true
      }
    )
    allScalaFiles.toSeq
  }
}
