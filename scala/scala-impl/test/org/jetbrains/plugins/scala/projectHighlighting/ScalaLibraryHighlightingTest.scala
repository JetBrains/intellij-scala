package org.jetbrains.plugins.scala
package projectHighlighting

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiManager
import org.junit.experimental.categories.Category

/**
 * Nikolay.Tropin
 * 27-Sep-17
 */
@Category(Array(classOf[HighlightingTests]))
class ScalaLibraryHighlightingTest extends base.ScalaLightCodeInsightFixtureTestAdapter {

  import base.libraryLoaders._
  import util.reporter.ProgressReporter

  private val filesWithProblems = Map(
    "scala/Responder.scala" -> Set[TextRange]((1987, 1988), (2133, 2134), (2278, 2279))
  )

  override def librariesLoaders = Seq(
    CustomSDKLoader,
    HeavyJDKLoader()
  )

  def testHighlightScalaLibrary(): Unit = {
    val reporter = ProgressReporter.newInstance(
      getClass.getSimpleName,
      filesWithProblems,
      reportStatus = false
    )
    val sourceRoot = CustomSDKLoader.sourceRoot

    VfsUtilCore.processFilesRecursively(
      sourceRoot,
      (file: VirtualFile) => {
        annotateFile(file, sourceRoot)(reporter)
        true
      }
    )
    reporter.reportResults()
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

  private object CustomSDKLoader extends ScalaSDKLoader {

    import DependencyManagerBase.DependencyDescription

    override protected def binaryDependencies(implicit version: ScalaVersion): List[DependencyDescription] =
      super.binaryDependencies.map(setCustomMinor)

    override protected def sourcesDependency(implicit version: ScalaVersion): DependencyDescription =
      setCustomMinor(super.sourcesDependency)

    private def setCustomMinor(description: DependencyDescription) =
      description.copy(version = "2.12.8")
  }

}
