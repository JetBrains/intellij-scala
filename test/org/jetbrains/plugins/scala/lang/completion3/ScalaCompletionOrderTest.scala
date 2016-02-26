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

  def testUnapplyInCaseCluase(): Unit ={
    checkPreferredItems(0, "arg")
  }

  def testSCL2022(){
    checkPreferredItems(0, "re", "replacer")
  }

  def UseNameAfterNew(): Unit ={
    checkPreferredItems(0, "Frost", "BadFrost")
  }

  def UseNameInCast(): Unit ={
    checkPreferredItems(0, "Frost")
  }

  def UseNameCaseLabelType(): Unit ={
    checkPreferredItems(0, "BadFrost")
  }

  def testUseNameWithError(): Unit ={
    checkPreferredItems(0, "Abrakadabra")
  }

  def UseNameWithErrorVarTypeSuggestion(): Unit ={
    checkPreferredItems(0, "Frost")
  }

  def testWithStat(): Unit = {
    val lookup = invokeCompletion(getTestName(false) + ".scala")
    assertPreferredItems(0, "fbar", "fboo")
    incUseCount(lookup, 1)
    assertPreferredItems(0, "fboo", "fbar")
  }
}
