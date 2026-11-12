package org.jetbrains.plugins.scala.lang.parser.incremental

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.{PsiDocumentManager, PsiErrorElement}
import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}
import org.jetbrains.plugins.scala.base.{ScalaCodeParsing, ScalaLightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.cc.ScCaptureRef
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert._

/**
 * Tests capture syntax through committed document edits, preserving capture references
 * and parser diagnostics. Compares each edited tree with a fresh parse to detect stale
 * PSI and contextual token remapping; fixed parser snapshots define the expected trees.
 */
class CaptureCheckingIncrementalParserTest extends ScalaLightCodeInsightFixtureTestCase with ScalaCodeParsing {
  private implicit def project: Project = getProject

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_8
  override def scalaCodeParsingFeatures: ScalaFeatures =
    ScalaFeatures.custom(ScalaVersion.Latest.Scala_3_8, hasCaptureCheckingEnabled = true)

  def testEditingLegacyCaptureSuffixes(): Unit = {
    checkEdits(
      "def f(c: Object^): Object^{c} = ???",
      Seq(
        ("^{c}", "^{c*.as[Capability].rd}", Seq("c*.as[Capability].rd")),
        ("*.as[Capability].rd", ".rd", Seq("c.rd")),
        (".rd", "", Seq("c"))
      )
    )
  }

  private def checkEdits(source: String, edits: Seq[(String, String, Seq[String])]): Unit = {
    val profile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val settings = profile.getSettings
    profile.setSettings(settings.copy(additionalCompilerOptions = settings.additionalCompilerOptions :+ "-language:experimental.captureChecking"))
    Disposer.register(getTestRootDisposable, () => profile.setSettings(settings))
    val file = configureFromFileText(ScalaFileType.INSTANCE, source)
    val manager = PsiDocumentManager.getInstance(getProject)
    val document = manager.getDocument(file)
    assertNotNull(document)
    assertTrue(file.depthFirst().filterByType[PsiErrorElement].isEmpty)

    for ((before, after, captures) <- edits) {
      val offset = document.getText.indexOf(before)
      assertTrue(s"Missing edit target: $before", offset >= 0)
      inWriteCommandAction {
        document.replaceString(offset, offset + before.length, after)
        manager.commitDocument(document)
      }(using getProject)
      assertTrue(file.depthFirst().filterByType[PsiErrorElement].isEmpty)
      assertEquals(captures, file.depthFirst().filterByType[ScCaptureRef].map(_.getText).toSeq)
      // Compare a committed edit with a fresh parse to detect stale PSI or token remapping.
      assertEquals(psiToString(parseScalaFile(document.getText), true).replace("dummy.scala", file.getName), psiToString(file, true))
    }
  }
}
