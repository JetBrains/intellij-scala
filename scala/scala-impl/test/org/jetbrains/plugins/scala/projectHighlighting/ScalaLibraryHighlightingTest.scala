package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.{ScalaFileType, SlowTests}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyLibraryLoader.Sources
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaLibraryLoader
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11, Scala_2_12}
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.junit.experimental.categories.Category


/**
  * Nikolay.Tropin
  * 27-Sep-17
  */
abstract class ScalaLibraryHighlightingTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testHighlightScalaLibrary(): Unit = {
    val reporter = ProgressReporter.newInstance(getClass.getName, reportSuccess = false)

    val sources = ScalaLibraryLoader.ScalaRuntimeLoader(Sources).rootFiles.head
    VfsUtilCore.processFilesRecursively(sources, (vFile: VirtualFile) => {
      if (vFile.getFileType == ScalaFileType.INSTANCE) {
        val relPath = VfsUtilCore.getRelativePath(vFile, sources)
        reporter.notify(relPath)

        val psiFile = PsiManager.getInstance(getProject).findFile(vFile)
        AllProjectHighlightingTest.annotateFile(psiFile, reporter, Some(relPath))
      }
      true
    })
    reporter.reportResults()
  }
}

@Category(Array(classOf[SlowTests]))
class ScalaLibraryHighlightingTest_2_12 extends ScalaLibraryHighlightingTest {
  override implicit val version: ScalaVersion = Scala_2_12
}