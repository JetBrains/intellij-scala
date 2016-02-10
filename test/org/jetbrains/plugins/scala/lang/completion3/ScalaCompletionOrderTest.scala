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

  def testLocaBeforeNameParams(): Unit = {
    checkPreferredItems(0, "namelocal", "nameParam")
  }

  def testChooseTypeWhenItExpected(): Unit = {
    checkPreferredItems(0, "fiTCase", "fiType", "fiTInClassType")
  }

  def testCaseClassParamCompletion(): Unit = {
    checkPreferredItems(0, "aname", "asurName", "aimark", "sporta")
  }

  def testWithStat(): Unit = {
    val lookup = invokeCompletion(getTestName(false) + ".scala")
    assertPreferredItems(0, "fbar", "fboo")
    incUseCount(lookup, 1)
    assertPreferredItems(0, "fboo", "fbar")
  }
}
