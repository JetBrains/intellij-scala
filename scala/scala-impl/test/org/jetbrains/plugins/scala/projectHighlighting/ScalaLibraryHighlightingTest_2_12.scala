package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.{HighlightingTests, ScalaVersion, ScalaFileType}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider.ScClsFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.junit.Assert
import org.junit.experimental.categories.Category
import org.jetbrains.plugins.scala.projectHighlighting.ImplicitConversions.tupleToTextRange

class ScalaLibraryHighlightingTest_2_12 extends ScalaLibraryHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_12

  override protected val filesWithProblems: Map[String, Set[TextRange]] = Map(
    "scala/reflect/ClassManifestDeprecatedApis.scala" -> Set(
      (2714, 2721), // Cannot resolve symbol subargs
      (2947, 2954), //Cannot resolve symbol subtype
    ),
  )
}

class ScalaLibraryHighlightingTest_2_13 extends ScalaLibraryHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override protected val filesWithProblems: Map[String, Set[TextRange]] = Map(
    "scala/reflect/ClassManifestDeprecatedApis.scala" -> Set(
      (2762, 2769), // Cannot resolve symbol subargs
      (2995, 3002), //Cannot resolve symbol subtype
    ),
  )
}

@Category(Array(classOf[HighlightingTests]))
abstract class ScalaLibraryHighlightingTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  private val CustomScalaSdkLoader = ScalaSDKLoader()

  override def librariesLoaders: Seq[LibraryLoader] = Seq(
    CustomScalaSdkLoader,
    HeavyJDKLoader()
  )

  protected def filesWithProblems: Map[String, Set[TextRange]] = Map()

  def testHighlightScalaLibrary(): Unit = {
    val reporter = ProgressReporter.newInstance(
      getClass.getSimpleName,
      filesWithProblems,
      reportStatus = false
    )
    val sourceRoot = CustomScalaSdkLoader.sourceRoot

    try {
      VfsUtilCore.processFilesRecursively(
        sourceRoot,
        (file: VirtualFile) => {
          annotateScalaFile(file, sourceRoot, reporter)
          true
        }
      )
      reporter.reportResults()
    } finally {
      System.err.println(s"### sourceRoot: $sourceRoot")
    }
  }

  def testAllSourcesAreFoundByRelativeFile(): Unit = {
    implicit val project: Project = getProject
    val classFilesFromScalaLibrary = for {
      className <- ScalaIndexKeys.ALL_CLASS_NAMES.allKeys
      psiClass  <- ScalaIndexKeys.ALL_CLASS_NAMES.elements(className, GlobalSearchScope.allScope(getProject))
      file      <- psiClass.getContainingFile.asOptionOf[ScClsFileImpl]
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

  private def annotateScalaFile(
    file: VirtualFile,
    ancestor: VirtualFile,
    reporter: ProgressReporter
  ): Unit =
    file.getFileType match {
      case ScalaFileType.INSTANCE =>
        val relPath = VfsUtilCore.getRelativePath(file, ancestor)
        reporter.notify(relPath)

        AllProjectHighlightingTest.annotateScalaFile(
          PsiManager.getInstance(getProject).findFile(file),
          reporter,
          Some(relPath)
        )
      case _ =>
    }
}
