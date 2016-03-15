package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType

/**
 * @author Alefas
 * @since 23.03.12
 */

class ScalaCompletionOrderTest extends ScalaCompletionSortingTestCase(CompletionType.BASIC, "/completion3/order/") {
  def testCaseClasuseParamAsLocal(): Unit = {
    checkPreferredItems(0, "retparam", "retField")
  }

  def testInImportSelector(): Unit = {
    checkPreferredItems(0, "foo3", "foo2", "foo1")
  }

  def testLocalBefore(): Unit = {
    checkPreferredItems(0, "fiValue", "field1", "fil1", "fil2", "fiFoo")
  }

  def testInInheritors(): Unit = {
    checkPreferredItems(0, "fok", "foo", "fol", "fos", "fob", "fooa")
  }

  def testLocalBeforeNameParams(): Unit = {
    checkPreferredItems(0, "namelocal", "nameParam")
  }

  def testChooseTypeWhenItExpected(): Unit = {
    checkPreferredItems(0, "fiTCase", "fiType", "fiTInClassType")
  }

  def testCaseClassParamCompletion(): Unit = {
    checkPreferredItems(0, "aname", "asurName", "aimark", "sporta")
  }

  def testUnapplyInCaseCluase(): Unit = {
    checkPreferredItems(0, "arg")
  }

  def testSCL2022() {
    checkPreferredItems(0, "re", "replacer")
  }

  def testSortByScope(): Unit = {
    checkPreferredItems(0, "v1", "v2", "v3")
  }

  def testUseNameAfterNew(): Unit = {
    checkPreferredItems(0, "Frost")
  }

  def testUseNameCaseLabelType(): Unit = {
    checkPreferredItems(0, "BadFrost")
  }

  def testUseNameWithError(): Unit = {
    checkPreferredItems(0, "Abrakadabra")
  }

  def testUseNameWithErrorVarTypeSuggestion(): Unit = {
    checkPreferredItems(0, "Frost")
  }

  def testUseNameInAssignement(): Unit = {
    checkPreferredItems(0, "Fast")
  }

  def testWithStat(): Unit = {
    val lookup = invokeCompletion(getTestName(false) + ".scala")
    assertPreferredItems(0, "fbar", "fboo")
    incUseCount(lookup, 1)
    assertPreferredItems(0, "fboo", "fbar")
  }
}
