package org.jetbrains.plugins.scala.lang.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt, PsiNamedElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.parser.ScalaParserTestOps.extractExpectedErrors
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert.assertEquals

trait ScalaParserTestOps {

  def parseText(text: String): ScalaFile

  def checkTree(text: String, expectedTree: String): Unit = {
    val file = parseText(text.withNormalizedSeparator)
    checkTree(file, expectedTree)
  }

  def checkTree(file: ScalaFile, expectedTree: String): Unit = {
    val resultTree = psiToString(file, true).replace(": " + file.name, "")
    assertEquals(expectedTree.trim.withNormalizedSeparator, resultTree.trim)
  }

  /**
   * Checks parser error messages and the complete PSI tree from a single parse of ordinary source.
   *
   * Error expectations are separate from the source so malformed input remains easy to read.
   * Every error message is compared in PSI traversal order, including repeated messages;
   * an empty expectation requires a parse without errors. No numeric offsets or ranges are checked.
   * The tree assertion still checks error-node ownership, structural placement and parser recovery.
   *
   * Expected messages also occur in the full tree. This duplication is intentional: the separate
   * list makes the diagnostic contract visible without reading the whole snapshot and produces
   * a focused failure before the tree comparison. A diagnostic change must update both expectations.
   *
   * The source must not contain [[err]] markers. Use [[checkParseErrors]] when a test deliberately
   * needs marker-based offset assertions instead of this message-only diagnostic contract.
   *
   * Examples below use independently written complete snapshots for the expected-tree arguments:
   * {{{
   * checkTreeAndErrors("type T = Int", expectedValidTree)
   *
   * checkTreeAndErrors(
   *   source = "type T = Object^{c.only}",
   *   expectedErrorMessages = Seq("'[' expected"),
   *   expectedTree = expectedMissingBracketTree
   * )
   *
   * checkTreeAndErrors(
   *   source = "class Box[C^ <: {any.except[], D^]",
   *   expectedErrorMessages = Seq("Wrong qualified identifier", "'}' expected"),
   *   expectedTree = expectedIncompleteBoundTree
   * )
   * }}}
   *
   * @param source ordinary Scala source, including malformed syntax, without test-error markers
   * @param expectedTree complete PSI snapshot, including the expected error elements and messages
   * @param expectedErrorMessages all error descriptions in PSI traversal order; empty by default
   */
  def checkTreeAndErrors(
    source: String,
    expectedTree: String,
    expectedErrorMessages: Seq[String] = Seq.empty
  ): Unit = {
    val file = checkParseErrorMessages(source, expectedErrorMessages)
    checkTree(file, expectedTree)
  }

  /** Checks loaded source/tree test data and requires it to parse without errors. */
  def checkTreeAndErrors(testData: ParserTreeTestData): Unit =
    checkTreeAndErrors(testData.source, testData.expectedTree)

  /**
   * Parses ordinary source once and checks every error description in PSI traversal order.
   * Preserves duplicate messages and rejects missing or unexpected errors without checking offsets.
   * The returned file lets recovery tests inspect the same parse after diagnostic validation.
   *
   * @param source Scala source without test-error markers; line separators are normalized
   * @param expectedErrorMessages complete error descriptions; empty by default to require no errors
   * @return the parsed file after its diagnostics have been checked
   */
  def checkParseErrorMessages(
    source: String,
    expectedErrorMessages: Seq[String] = Seq.empty
  ): ScalaFile = {
    val file = parseText(source.withNormalizedSeparator)
    val actualErrorMessages = file.depthFirst().toSeq.filterByType[PsiErrorElement].map(_.getErrorDescription)
    assertEquals(
      s"Parser error messages do not match for source:\n$source",
      expectedErrorMessages,
      actualErrorMessages
    )
    file
  }

  def err(err: String): String =
    s"[[Err($err)]]"

  def checkParseErrors(text: String): ScalaFile = {
    val (code, expectedErrors) = extractExpectedErrors(text.withNormalizedSeparator)

    val file = parseText(code)
    val errors = file.depthFirst().toSeq.filterByType[PsiErrorElement]
    val errorsWithPos = errors.map(psi => psi.getTextOffset -> psi.getErrorDescription).toSet
    assertEquals("parse errors do not match", expectedErrors.toSeq.sorted, errorsWithPos.toSeq.sorted)
    file
  }
}

object ScalaParserTestOps {

  private val errRegex = raw"\[\[Err\((.+)\)\]\]".r

  def extractExpectedErrors(text: String): (String, Set[(Int, String)]) = {
    var code = text
    val errorsBuilder = Set.newBuilder[(Int, String)]
    while (true) {
      errRegex.findFirstMatchIn(code) match {
        case Some(matc) =>
          code = code.substring(0, matc.start) + code.substring(matc.end)
          errorsBuilder += (matc.start -> matc.group(1))
        case None =>
          return code -> errorsBuilder.result()
      }
    }
    ???
  }
}
