package org.jetbrains.plugins.scala.util

import junit.framework.TestCase
import org.jetbrains.plugins.scala.AssertionMatchers._

class IndentUtilTest extends TestCase {

  def testAppendSpacesToEmptyIndentString_EmptyString(): Unit = {
    val input = ""
    IndentUtil.appendSpacesToIndentString(input, spaces = 1, tabSize = 4) shouldBe " "
    IndentUtil.appendSpacesToIndentString(input, spaces = 2, tabSize = 4) shouldBe "  "
    IndentUtil.appendSpacesToIndentString(input, spaces = 3, tabSize = 4) shouldBe "   "
    IndentUtil.appendSpacesToIndentString(input, spaces = 4, tabSize = 4) shouldBe "\t"
    IndentUtil.appendSpacesToIndentString(input, spaces = 5, tabSize = 4) shouldBe "\t "
    IndentUtil.appendSpacesToIndentString(input, spaces = 6, tabSize = 4) shouldBe "\t  "
    IndentUtil.appendSpacesToIndentString(input, spaces = 7, tabSize = 4) shouldBe "\t   "
    IndentUtil.appendSpacesToIndentString(input, spaces = 8, tabSize = 4) shouldBe "\t\t"
  }

  def testAppendSpacesToNonEmptyIndentStringWithTabsOnly(): Unit = {
    val input = "\t\t"
    IndentUtil.appendSpacesToIndentString(input, spaces = 1, tabSize = 4) shouldBe "\t\t "
    IndentUtil.appendSpacesToIndentString(input, spaces = 2, tabSize = 4) shouldBe "\t\t  "
    IndentUtil.appendSpacesToIndentString(input, spaces = 3, tabSize = 4) shouldBe "\t\t   "
    IndentUtil.appendSpacesToIndentString(input, spaces = 4, tabSize = 4) shouldBe "\t\t\t"
    IndentUtil.appendSpacesToIndentString(input, spaces = 5, tabSize = 4) shouldBe "\t\t\t "
    IndentUtil.appendSpacesToIndentString(input, spaces = 6, tabSize = 4) shouldBe "\t\t\t  "
    IndentUtil.appendSpacesToIndentString(input, spaces = 7, tabSize = 4) shouldBe "\t\t\t   "
    IndentUtil.appendSpacesToIndentString(input, spaces = 8, tabSize = 4) shouldBe "\t\t\t\t"
  }


  def testAppendSpacesToNonEmptyIndentStringWithTabsAndSpaces(): Unit = {
    val input = "\t\t "
    IndentUtil.appendSpacesToIndentString(input, spaces = 1, tabSize = 4) shouldBe "\t\t  "
    IndentUtil.appendSpacesToIndentString(input, spaces = 2, tabSize = 4) shouldBe "\t\t   "
    IndentUtil.appendSpacesToIndentString(input, spaces = 3, tabSize = 4) shouldBe "\t\t\t"
    IndentUtil.appendSpacesToIndentString(input, spaces = 4, tabSize = 4) shouldBe "\t\t\t "
    IndentUtil.appendSpacesToIndentString(input, spaces = 5, tabSize = 4) shouldBe "\t\t\t  "
    IndentUtil.appendSpacesToIndentString(input, spaces = 6, tabSize = 4) shouldBe "\t\t\t   "
    IndentUtil.appendSpacesToIndentString(input, spaces = 7, tabSize = 4) shouldBe "\t\t\t\t"
    IndentUtil.appendSpacesToIndentString(input, spaces = 8, tabSize = 4) shouldBe "\t\t\t\t "
  }

  def testAppendSpacesToNonEmptyIndentStringWithTabsAndSpacesInTheMiddle(): Unit = {
    val input = "\t \t"
    IndentUtil.appendSpacesToIndentString(input, spaces = 1, tabSize = 4) shouldBe "\t\t  "
    IndentUtil.appendSpacesToIndentString(input, spaces = 2, tabSize = 4) shouldBe "\t\t   "
    IndentUtil.appendSpacesToIndentString(input, spaces = 3, tabSize = 4) shouldBe "\t\t\t"
    IndentUtil.appendSpacesToIndentString(input, spaces = 4, tabSize = 4) shouldBe "\t\t\t "
    IndentUtil.appendSpacesToIndentString(input, spaces = 5, tabSize = 4) shouldBe "\t\t\t  "
    IndentUtil.appendSpacesToIndentString(input, spaces = 6, tabSize = 4) shouldBe "\t\t\t   "
    IndentUtil.appendSpacesToIndentString(input, spaces = 7, tabSize = 4) shouldBe "\t\t\t\t"
    IndentUtil.appendSpacesToIndentString(input, spaces = 8, tabSize = 4) shouldBe "\t\t\t\t "
  }
}