package org.jetbrains.plugins.scala.lang.parser.incremental

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.DebugUtil.psiToString
import com.intellij.psi.{PsiDocumentManager, PsiErrorElement, PsiFile}
import org.jetbrains.plugins.scala.base.{CaptureCheckingTestUtils, ScalaCodeParsing, ScalaLightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.base.types.cc.ScCaptureRef
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}
import org.junit.Assert._

import scala.jdk.CollectionConverters._

/**
 * Tests capture syntax through committed document edits, preserving capture references
 * and parser diagnostics. Compares each edited tree with a fresh parse to detect stale
 * PSI and contextual token remapping; fixed parser snapshots define the expected trees.
 * Covers filter keyword highlighting and parameter recovery across marker/delimiter edits.
 */
class CaptureCheckingIncrementalParserTest extends ScalaLightCodeInsightFixtureTestCase with ScalaCodeParsing {
  private implicit def project: Project = getProject

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_8
  override protected def additionalCompilerOptions: Seq[String] = CaptureCheckingTestUtils.compilerOptions
  override def scalaCodeParsingFeatures: ScalaFeatures = CaptureCheckingTestUtils.parsingFeatures

  def testEditingLegacyCaptureSuffixes(): Unit = {
    checkCommittedCaptureEdits(
      "def f(c: Object^): Object^{c} = ???",
      Seq(
        CaptureEdit("^{c}", "^{c*.as[Capability].rd}", Seq("c*.as[Capability].rd")),
        CaptureEdit("*.as[Capability].rd", ".rd", Seq("c.rd")),
        CaptureEdit(".rd", "", Seq("c"))
      )
    )
  }

  def testEditingCaptureFiltersAndKeywordHighlighting(): Unit = {
    checkCommittedCaptureEdits(
      "def f(c: Object^): Object^{c.only[C]} = ???\nval only = 1\nval except = 2",
      Seq(
        CaptureEdit(
          ".only[C]", ".only[C].except[D].rd", Seq("c.only[C].except[D].rd"),
          expectedCaptureKeywords = Some(Seq("only", "except", "rd"))
        ),
        CaptureEdit(
          ".except[D]", ".except[]", Seq("c.only[C].except[].rd"),
          expectedParserErrors = Seq("Wrong qualified identifier")
        ),
        CaptureEdit(
          ".except[]", ".except[D]", Seq("c.only[C].except[D].rd"),
          expectedCaptureKeywords = Some(Seq("only", "except", "rd"))
        ),
        CaptureEdit(
          ".only[C]", "", Seq("c.except[D].rd"),
          expectedCaptureKeywords = Some(Seq("except", "rd"))
        )
      )
    )
  }

  def testEditingCaptureParameterMarkersAndBoundDelimiters(): Unit = {
    checkCommittedCaptureEdits(
      "def f[C^ <: {any.except[Control]}, T](x: T): T = x\ndef after = ()",
      Seq(
        CaptureEdit("C^", "C", Seq("any.except[Control]")),
        CaptureEdit("C <:", "C^ <:", Seq("any.except[Control]")),
        CaptureEdit(
          "}, T", ", T: Ordering", Seq("any.except[Control]"),
          expectedParserErrors = Seq("'}' expected")
        ),
        CaptureEdit(
          ", T: Ordering", "}, T: Ordering", Seq("any.except[Control]"),
          expectedCaptureKeywords = Some(Seq("except"))
        )
      ),
      expectedTypeParameterNames = Seq("C", "T")
    )
  }

  def testDeletingCaptureBoundBraceBeforeAnnotatedVariantParameter(): Unit = {
    checkCommittedCaptureEdits(
      "class Box[C^ <: {any}, @ann +T]\nclass After",
      Seq(
        CaptureEdit("},", ",", Seq("any"), expectedParserErrors = Seq("'}' expected")),
        CaptureEdit(", @ann", "}, @ann", Seq("any"))
      ),
      expectedTypeParameterNames = Seq("C", "T")
    )
  }

  /**
   * One replacement in the current document and the expected state after committing it.
   *
   * @param textToReplace the first matching substring to replace; it must exist
   * @param replacementText text to insert in place of that substring
   * @param expectedCaptureReferences all capture-reference texts in PSI traversal order
   * @param expectedParserErrors all parser-error descriptions in PSI traversal order
   * @param expectedCaptureKeywords only/except/rd keyword highlights in source order;
   *                                None skips highlighting, while Some(Seq.empty) requires none
   */
  private case class CaptureEdit(
    textToReplace: String,
    replacementText: String,
    expectedCaptureReferences: Seq[String],
    expectedParserErrors: Seq[String] = Seq.empty,
    expectedCaptureKeywords: Option[Seq[String]] = None
  )

  /**
   * Applies a sequence of capture-syntax edits to one fixture file, committing each edit.
   * After every commit, checks diagnostics, type-parameter names, capture references and
   * optional keyword highlighting, then compares the complete PSI tree with a fresh parse
   * of the current document to detect stale PSI or incorrect contextual token remapping.
   *
   * @param source initial source, which must parse without errors
   * @param edits replacements applied in order to the evolving document, with per-step expectations
   * @param expectedTypeParameterNames all type-parameter names in PSI traversal order after every
   *                                   edit; the default requires that there are no type parameters
   */
  private def checkCommittedCaptureEdits(
    source: String,
    edits: Seq[CaptureEdit],
    expectedTypeParameterNames: Seq[String] = Seq.empty
  ): Unit = {
    val file = configureFromFileText(ScalaFileType.INSTANCE, source)
    val manager = PsiDocumentManager.getInstance(getProject)
    val document = manager.getDocument(file)
    assertNotNull(document)
    assertTrue(file.depthFirst().filterByType[PsiErrorElement].isEmpty)

    for (edit <- edits) {
      applyAndCommitEdit(document, manager, edit.textToReplace, edit.replacementText)

      assertEquals(edit.expectedParserErrors, file.depthFirst().filterByType[PsiErrorElement].map(_.getErrorDescription).toSeq)
      assertEquals(expectedTypeParameterNames, file.depthFirst().filterByType[ScTypeParam].map(_.name).toSeq)
      edit.expectedCaptureKeywords.foreach(assertCaptureKeywordHighlighting(document, _))
      assertEquals(edit.expectedCaptureReferences, file.depthFirst().filterByType[ScCaptureRef].map(_.getText).toSeq)

      assertMatchesFreshParse(file, document.getText)
    }
  }

  private def applyAndCommitEdit(document: Document, manager: PsiDocumentManager, before: String, after: String): Unit = {
    val offset = document.getText.indexOf(before)
    assertTrue(s"Missing edit target: $before", offset >= 0)
    inWriteCommandAction {
      document.replaceString(offset, offset + before.length, after)
      manager.commitDocument(document)
    }(using getProject)
  }

  private def assertCaptureKeywordHighlighting(document: Document, expected: Seq[String]): Unit = {
    val actual = myFixture.doHighlighting().asScala.filter { info =>
      Option(info.forcedTextAttributesKey).getOrElse(info.`type`.getAttributesKey) == DefaultHighlighter.KEYWORD
    }.sortBy(_.getStartOffset)
      .map(info => document.getText.substring(info.getStartOffset, info.getEndOffset))
      .filter(Set("only", "except", "rd"))
    assertEquals(expected, actual.toSeq)
  }

  private def assertMatchesFreshParse(file: PsiFile, source: String): Unit = {
    // Compare a committed edit with a fresh parse to detect stale PSI or token remapping.
    val freshTree = psiToString(parseScalaFile(source), true).replace("dummy.scala", file.getName)
    assertEquals(freshTree, psiToString(file, true))
  }
}
