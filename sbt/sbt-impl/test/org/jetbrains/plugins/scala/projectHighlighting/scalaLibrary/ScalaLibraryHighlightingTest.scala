package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.annotator.HighlightingAdvisor
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider.ScClsFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt
import org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.RevertableChange
import org.jetbrains.plugins.scala.{HighlightingTests, ScalaFileType}
import org.junit.Assert
import org.junit.experimental.categories.Category

import scala.collection.mutable

@Category(Array(classOf[HighlightingTests]))
abstract class ScalaLibraryHighlightingTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken.DoNotShare

  private val CustomScalaSdkLoader = ScalaSDKLoader()

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    CustomScalaSdkLoader,
    HeavyJDKLoader()
  )

  protected def filesWithProblems: Map[String, Set[TextRange]] = Map()

  override def setUp(): Unit = {
    super.setUp()
    val revertible = RevertableChange.withModifiedSetting[Boolean](
      HighlightingAdvisor.typeAwareHighlightingForScalaLibrarySourcesEnabled,
      HighlightingAdvisor.typeAwareHighlightingForScalaLibrarySourcesEnabled = _,
      true
    )
    revertible.applyChange(getTestRootDisposable)
  }

  //NOTE: implementation is very similar to
  //org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest.doAllProjectHighlightingTest
  def testHighlightScalaLibrary(): Unit = {
    val reporter = HighlightingProgressReporter.newInstance(getClass.getSimpleName, filesWithProblems)
    val sourceRoot = CustomScalaSdkLoader.sourceRoot

    try {
      val scalaFiles = ScalaLibraryHighlightingTest.findAllScalaFiles(sourceRoot).sortBy(_.getPath)

      val fileManager = PsiManager.getInstance(getProject).asInstanceOf[PsiManagerEx].getFileManager

      AllProjectHighlightingTest.warnIfUsingRandomizedTests(reporter)

      val filesTotal = scalaFiles.size
      for ((file, fileIndex) <- scalaFiles.zipWithIndex) {
        val psiFile = fileManager.findFile(file)

        val fileRelativePath = VfsUtilCore.getRelativePath(file, sourceRoot)
        reporter.notifyHighlightingProgress(fileIndex, filesTotal, fileRelativePath)

        AllProjectHighlightingTest.annotateScalaFile(psiFile, reporter, fileRelativePath)
      }

      reporter.reportFinalResults()
    } finally {
      System.err.println(s"### sourceRoot: $sourceRoot")
    }
  }

  protected def scalaLibraryJarName: String = "scala-library"

  def testAllSourcesAreFoundByRelativeFile(): Unit = {
    implicit val project: Project = getProject
    val classFilesFromScalaLibrary = for {
      className <- ScalaIndexKeys.ALL_CLASS_NAMES.allKeys
      psiClass <- ScalaIndexKeys.ALL_CLASS_NAMES.elements(className, GlobalSearchScope.allScope(getProject))
      file <- psiClass.getContainingFile.asOptionOf[ScClsFileImpl]
      if file.getVirtualFile.getPath.contains(scalaLibraryJarName)
    } yield file

    Assert.assertTrue(s"Too few class files found in scala-library: ${classFilesFromScalaLibrary.size}", classFilesFromScalaLibrary.size > 100)

    classFilesFromScalaLibrary.foreach { file =>
      val sourceFile = file.findSourceByRelativePath
      Assert.assertTrue(
        s"Source file for ${file.getVirtualFile.getPath} was not found by relative path",
        sourceFile.nonEmpty
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