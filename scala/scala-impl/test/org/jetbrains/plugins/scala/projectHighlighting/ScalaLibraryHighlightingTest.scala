package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.DependencyManagerBase._
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

  val filesWithProblems: Map[String, Seq[(Int, Int)]]

  def testHighlightScalaLibrary(): Unit = {
    val reporter = ProgressReporter.newInstance(getClass.getName, filesWithProblems, reportSuccess = false)
    val sources = DependencyManager.resolveSingle("org.scala-lang" % "scala-library" % version.minor % Types.SRC).toJarVFile
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

  override val filesWithProblems = Map(
    "scala/Responder.scala" ->
      Seq((2258, 2259), (2404, 2405), (2549, 2550)),
    "scala/ref/SoftReference.scala" ->
      Seq((731, 741)),
    "scala/ref/WeakReference.scala" ->
      Seq((944, 954)),
    "scala/ref/ReferenceQueue.scala" ->
      Seq((664, 674)),
    "scala/ref/PhantomReference.scala" ->
      Seq((689, 699)),
    "scala/ref/ReferenceWrapper.scala" ->
      Seq((657, 667), (975, 979)),
    "scala/collection/parallel/Tasks.scala" ->
      Seq((12526, 12565), (12571, 12622), (12993, 13034), (13040, 13091)),
    "scala/collection/immutable/List.scala" ->
      Seq((6725, 6741))
  )

  override implicit val version: ScalaVersion = Scala_2_12
}