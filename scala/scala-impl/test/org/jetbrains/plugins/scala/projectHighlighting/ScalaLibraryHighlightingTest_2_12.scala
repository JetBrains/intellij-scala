package org.jetbrains.plugins.scala
package projectHighlighting

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider.ScClsFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.junit.Assert
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class ScalaLibraryHighlightingTest_2_12 extends ScalaLightCodeInsightFixtureTestAdapter {

  import org.jetbrains.plugins.scala.projectHighlighting.ImplicitConversions.tupleToTextRange
  
  private val filesWithProblems: Map[String, Set[TextRange]] = Map(
    "scala/reflect/ClassManifestDeprecatedApis.scala" -> Set((2714, 2721),(2947, 2954)),
  )

  private val CustomScalaSdkLoader = ScalaSDKLoader()

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.fromString("2.12.8").get

  override def librariesLoaders = Seq(
    CustomScalaSdkLoader,
    HeavyJDKLoader()
  )

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
          annotateFile(file, sourceRoot)(reporter)
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

  private def annotateFile(file: VirtualFile,
                           ancestor: VirtualFile)
                          (implicit reporter: ProgressReporter): Unit =
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
