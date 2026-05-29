package org.jetbrains.sbt.process.options.parsing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit coverage for comment stripping and quote-balance preprocessing.
 */
class CommentsAndQuotesPreprocessorTest {

  private def assertPreprocessed(input: String, expected: Option[String]): Unit = {
    val actual = CommentsAndQuotesPreprocessor.preprocess(input)
    assertEquals(expected, actual)
  }

  @Test
  def keepsBalancedQuotedPartsIntact_1_doubleQuotedInput(): Unit =
    assertPreprocessed(""" "aaa'bbb" """, Some(""" "aaa'bbb" """))

  @Test
  def keepsBalancedQuotedPartsIntact_2_singleQuotedInputWithHashInsideQuotes(): Unit =
    assertPreprocessed(""" 'aaa"bbb''#ccc' """, Some(""" 'aaa"bbb''#ccc' """))

  @Test
  def keepsBalancedQuotedPartsIntact_3_mixedQuotesWithHashOutsideQuotes(): Unit =
    assertPreprocessed(""" 'aaa"bbb'"ccc #" ddd """, Some(""" 'aaa"bbb'"ccc #" ddd """))

  @Test
  def removesCommentedOutPartsOutsideQuotes_1_afterDoubleQuotedInput(): Unit =
    assertPreprocessed(""" "aaa'bbb" #ccc """, Some(""" "aaa'bbb" """))

  @Test
  def removesCommentedOutPartsOutsideQuotes_2_afterSingleQuotedInput(): Unit =
    assertPreprocessed(""" 'aaa"bbb'#ccc """, Some(""" 'aaa"bbb'"""))

  @Test
  def returnsEmptyStringForCommentOnlyInput(): Unit =
    assertPreprocessed("""#'aaa"bbb'#ccc """, Some(""))

  @Test
  def removesCommentedOutPartsPerLineInMultilineInput(): Unit =
    assertPreprocessed("command # comment\nargument", Some("command \nargument"))

  @Test
  def returnsNoneForUnbalancedDoubleQuotes_1_afterCommentMarkerInSingleQuotedPart(): Unit =
    assertPreprocessed(""" "aaa'bbb'#ccc """, None)

  @Test
  def returnsNoneForUnbalancedDoubleQuotes_2_afterSingleQuotedPart(): Unit =
    assertPreprocessed(""" 'aaa"bbb'  " """, None)

  @Test
  def returnsNoneForUnbalancedDoubleQuotes_3_beforeTrailingText(): Unit =
    assertPreprocessed(""" 'aaa"bbb'ccc  "ddd """, None)

  @Test
  def returnsNoneForUnbalancedSingleQuotes_1_beforeTrailingText(): Unit =
    assertPreprocessed(""" 'aaa"bbb'ccc  'ddd """, None)

  @Test
  def returnsNoneForUnbalancedSingleQuotes_2_missingClosingQuote(): Unit =
    assertPreprocessed(""" 'aaa"bbb"ccc  ddd """, None)
}
