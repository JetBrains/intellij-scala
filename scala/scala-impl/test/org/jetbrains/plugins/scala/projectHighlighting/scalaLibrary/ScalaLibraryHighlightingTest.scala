package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider.ScClsFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt
import org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.{HighlightingTests, ScalaFileType}
import org.junit.Assert
import org.junit.experimental.categories.Category

import scala.collection.mutable

@Category(Array(classOf[HighlightingTests]))
abstract class ScalaLibraryHighlightingTest extends ScalaLightCodeInsightFixtureTestCase {

  private val CustomScalaSdkLoader = ScalaSDKLoader()

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    CustomScalaSdkLoader,
    HeavyJDKLoader()
  )

  protected def filesWithProblems: Map[String, Set[TextRange]] = Map()

  //NOTE: implementation is very similar to
  //org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest.doAllProjectHighlightingTest
  def testHighlightScalaLibrary(): Unit = {
    val reporter = HighlightingProgressReporter.newInstance(getClass.getSimpleName, filesWithProblems)
    val sourceRoot = CustomScalaSdkLoader.sourceRoot

    try {
      val scalaFiles = ScalaLibraryHighlightingTest.findAllScalaFiles(sourceRoot)

      val fileManager = PsiManager.getInstance(getProject).asInstanceOf[PsiManagerEx].getFileManager

      AllProjectHighlightingTest.warnIfUsingRandomizedTests(reporter)

      val filesTotal = scalaFiles.size
      for ((file, fileIndex) <- scalaFiles.zipWithIndex) {
        val psiFile = fileManager.findFile(file)

        val fileRelativePath = VfsUtilCore.getRelativePath(file, sourceRoot)
        reporter.notifyHighlightingProgress(fileIndex, filesTotal, fileRelativePath)

        AllProjectHighlightingTest.annotateScalaFile(
          psiFile,
          reporter,
          Some(fileRelativePath)
        )
      }

      reporter.reportFinalResults()
    } finally {
      System.err.println(s"### sourceRoot: $sourceRoot")
    }
  }

  def testAllSourcesAreFoundByRelativeFile(): Unit = {
    implicit val project: Project = getProject
    val classFilesFromScalaLibrary = for {
      className <- ScalaIndexKeys.ALL_CLASS_NAMES.allKeys
      psiClass <- ScalaIndexKeys.ALL_CLASS_NAMES.elements(className, GlobalSearchScope.allScope(getProject))
      file <- psiClass.getContainingFile.asOptionOf[ScClsFileImpl]
      if file.getVirtualFile.getPath.contains("scala-library")
    } yield file

    Assert.assertTrue("Too few class files found in scala-library", classFilesFromScalaLibrary.size > 1000)

    classFilesFromScalaLibrary.foreach { file =>
      Assert.assertTrue(
        s"Source file for ${file.getVirtualFile.getPath} was not found by relative path",
        file.findSourceByRelativePath.nonEmpty
      )
    }
  }
}

object ScalaLibraryHighlightingTest {

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