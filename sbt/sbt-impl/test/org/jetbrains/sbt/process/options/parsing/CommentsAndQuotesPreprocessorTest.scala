package org.jetbrains.sbt.process.options.parsing

import org.jetbrains.sbt.process.options.parsing.CommentsAndQuotesPreprocessor.PreprocessResult
import org.jetbrains.sbt.process.options.parsing.model.MalformedSbtOption
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit coverage for comment stripping and quote-balance preprocessing.
 */
class CommentsAndQuotesPreprocessorTest {

  private def assertPreprocessed(input: String, expected: PreprocessResult): Unit = {
    val actual = CommentsAndQuotesPreprocessor.preprocess(input)
    assertEquals(expected, actual)
  }

  @Test
  def keepsBalancedQuotedPartsIntact_1_doubleQuotedInput(): Unit =
    assertPreprocessed(""" "aaa'bbb" """, PreprocessResult(Some(""" "aaa'bbb" """), Seq.empty))

  @Test
  def keepsBalancedQuotedPartsIntact_2_singleQuotedInputWithHashInsideQuotes(): Unit =
    assertPreprocessed(""" 'aaa"bbb''#ccc' """, PreprocessResult(Some(""" 'aaa"bbb''#ccc' """), Seq.empty))

  @Test
  def keepsBalancedQuotedPartsIntact_3_mixedQuotesWithHashOutsideQuotes(): Unit =
    assertPreprocessed(""" 'aaa"bbb'"ccc #" ddd """, PreprocessResult(Some(""" 'aaa"bbb'"ccc #" ddd """), Seq.empty))

  @Test
  def removesCommentedOutPartsOutsideQuotes_1_afterDoubleQuotedInput(): Unit =
    assertPreprocessed(""" "aaa'bbb" #ccc """, PreprocessResult(Some(""" "aaa'bbb" """), Seq.empty))

  @Test
  def removesCommentedOutPartsOutsideQuotes_2_afterSingleQuotedInput(): Unit =
    assertPreprocessed(""" 'aaa"bbb'#ccc """, PreprocessResult(Some(""" 'aaa"bbb'"""), Seq.empty))

  @Test
  def returnsEmptyStringForCommentOnlyInput(): Unit =
    assertPreprocessed("""#'aaa"bbb'#ccc """, PreprocessResult(Some(""), Seq.empty))

  @Test
  def removesCommentedOutPartsPerLineInMultilineInput(): Unit =
    assertPreprocessed("command # comment\nargument", PreprocessResult(Some("command \nargument"), Seq.empty))

  @Test
  def returnsNoneForUnbalancedDoubleQuotes_1_afterCommentMarkerInSingleQuotedPart(): Unit =
    assertPreprocessed(""" "aaa'bbb'#ccc """, PreprocessResult(None, Seq(MalformedSbtOption(1, '"', """ "aaa'bbb'#ccc """))))

  @Test
  def returnsNoneForUnbalancedDoubleQuotes_2_afterSingleQuotedPart(): Unit =
    assertPreprocessed(""" 'aaa"bbb'  " """, PreprocessResult(None, Seq(MalformedSbtOption(1, '"', """ 'aaa"bbb'  " """))))

  @Test
  def returnsNoneForUnbalancedDoubleQuotes_3_beforeTrailingText(): Unit =
    assertPreprocessed(""" 'aaa"bbb'ccc  "ddd """, PreprocessResult(None, Seq(MalformedSbtOption(1, '"', """ 'aaa"bbb'ccc  "ddd """))))

  @Test
  def returnsNoneForUnbalancedSingleQuotes_1_beforeTrailingText(): Unit =
    assertPreprocessed(""" 'aaa"bbb'ccc  'ddd """, PreprocessResult(None, Seq(MalformedSbtOption(1, '\'', """ 'aaa"bbb'ccc  'ddd """))))

  @Test
  def returnsNoneForUnbalancedSingleQuotes_2_missingClosingQuote(): Unit =
    assertPreprocessed(""" 'aaa"bbb"ccc  ddd """, PreprocessResult(None, Seq(MalformedSbtOption(1, '\'', """ 'aaa"bbb"ccc  ddd """))))

  @Test
  def reportsUnbalancedQuoteLine(): Unit =
    assertPreprocessed(
      "-debug\n-sbt-dir \"/tmp/sbt",
      PreprocessResult(None, Seq(MalformedSbtOption(lineNumber = 2, unclosedQuote = '"', lineContent = "-sbt-dir \"/tmp/sbt")))
    )
}
