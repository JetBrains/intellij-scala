package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionSortingTestBase
import org.junit.Test

class ScalaCompletionOrderTest extends ScalaCompletionSortingTestBase {

  override def getTestDataPath: String =
    super.getTestDataPath + "order/"

  @Test
  def testCaseClauseParamAsLocal(): Unit =
    checkFirst("retLocal", "retparam", "retField")

  @Test
  def testInImportSelector(): Unit =
    checkFirst("foo3", "foo2", "foo1")

  @Test
  def testLocalBefore(): Unit =
    checkFirst("fiValue", "field1", "fil1", "fil2", "fiFoo")

  @Test
  def testInInheritors(): Unit =
    checkFirst("fok", "foo", "fol", "fos", "fob", "fooa")

  @Test
  def testLocalBeforeNameParams(): Unit =
    checkFirst("namelocal", "nameParam")

  @Test
  def testChooseTypeWhenItExpected(): Unit =
    checkFirst("fiTCase", "fiType", "fiTInClassType")

  @Test
  def testCaseClassParamCompletion(): Unit =
    checkFirst("aname", "asurName", "aimark", "sporta")

  @Test
  def testUnapplyInCaseClause(): Unit =
    checkFirst("arg")

  @Test
  def testSCL2022(): Unit =
    checkFirst("re", "replacer")

  @Test
  def testSortByScope(): Unit =
    checkFirst("v1", "v2", "v3")

  @Test
  def testUseNameAfterNew(): Unit =
    checkFirst("Frost")

  @Test
  def testUseNameCaseLabelType(): Unit =
    checkFirst("BadFrost")

  @Test
  def testUseNameWithError(): Unit =
    checkFirst("Abrakadabra")

  @Test
  def testUseNameWithErrorVarTypeSuggestion(): Unit =
    checkFirst("Frost")

  @Test
  def testUseNameInAssignment(): Unit =
    checkFirst("Fast")

  @Test
  def testBackticks1(): Unit =
    checkFirst("`type`", "typeSystem", "fromtype")

  @Test
  def testBackticks2(): Unit =
    checkFirst("`type`", "typeSystem")

  @Test
  def testWithStat(): Unit = {
    checkFirst("fbar", "fboo")

    incUseCount()
    myFixture.assertPreferredCompletionItems(0, "fboo", "fbar")
  }

  @Test
  def testForGenerator(): Unit =
    checkFirst("ir", "iSeq", "iParam")
}
  

