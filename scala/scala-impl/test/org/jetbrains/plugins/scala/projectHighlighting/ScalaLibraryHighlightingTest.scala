package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.jetbrains.plugins.scala.{DependencyManager, HighlightingTests, ScalaFileType}
import org.junit.experimental.categories.Category


/**
  * Nikolay.Tropin
  * 27-Sep-17
  */
abstract class ScalaLibraryHighlightingTest extends ScalaLightCodeInsightFixtureTestAdapter {

  val filesWithProblems: Set[String]

  def testHighlightScalaLibrary(): Unit = {
    val reporter = ProgressReporter.newInstance(getClass.getName, filesWithProblems, reportSuccess = false)
    val sources = DependencyManager().resolve("org.scala-lang" % "scala-library" % version.minor % Types.SRC)
      .head
      .toJarVFile
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

@Category(Array(classOf[HighlightingTests]))
class ScalaLibraryHighlightingTest_2_12 extends ScalaLibraryHighlightingTest {

  override val filesWithProblems = Set(
    "scala/Responder.scala",
    "scala/ref/SoftReference.scala",
    "scala/ref/WeakReference.scala",
    "scala/ref/ReferenceQueue.scala",
    "scala/ref/PhantomReference.scala",
    "scala/ref/ReferenceWrapper.scala",
    "scala/collection/parallel/Tasks.scala",
    "scala/collection/immutable/List.scala"
  )

  override implicit val version: ScalaVersion = Scala_2_12
}