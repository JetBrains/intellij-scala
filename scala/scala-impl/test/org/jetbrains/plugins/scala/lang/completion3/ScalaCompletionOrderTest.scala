package org.jetbrains.plugins.scala.lang.completion3

class ScalaCompletionOrderTest extends ScalaCompletionSortingTestCase {

  override def getTestDataPath: String =
    super.getTestDataPath + "order/"

  def testCaseClauseParamAsLocal(): Unit =
    checkFirst("retparam", "retField")

  def testInImportSelector(): Unit =
    checkFirst("foo3", "foo2", "foo1")

  def testLocalBefore(): Unit =
    checkFirst("fiValue", "field1", "fil1", "fil2", "fiFoo")

  def testInInheritors(): Unit =
    checkFirst("fok", "foo", "fol", "fos", "fob", "fooa")

  def testLocalBeforeNameParams(): Unit =
    checkFirst("namelocal", "nameParam")

  def testChooseTypeWhenItExpected(): Unit =
    checkFirst("fiTCase", "fiType", "fiTInClassType")

  def testCaseClassParamCompletion(): Unit =
    checkFirst("aname", "asurName", "aimark", "sporta")

  def testUnapplyInCaseClause(): Unit =
    checkFirst("arg")

  def testSCL2022(): Unit =
    checkFirst("re", "replacer")

  def testSortByScope(): Unit =
    checkFirst("v1", "v2", "v3")

  def testUseNameAfterNew(): Unit =
    checkFirst("Frost")

  def testUseNameCaseLabelType(): Unit =
    checkFirst("BadFrost")

  def testUseNameWithError(): Unit =
    checkFirst("Abrakadabra")

  def testUseNameWithErrorVarTypeSuggestion(): Unit =
    checkFirst("Frost")

  def testUseNameInAssignment(): Unit =
    checkFirst("Fast")

  def testBackticks1(): Unit =
    checkFirst("`type`", "typeSystem", "fromtype")

  def testBackticks2(): Unit =
    checkFirst("`type`", "typeSystem")

  def testWithStat(): Unit = {
    checkFirst("fbar", "fboo")

    incUseCount()
    myFixture.assertPreferredCompletionItems(0, "fboo", "fbar")
  }

  def testForGenerator(): Unit =
    checkFirst("ir", "iSeq", "iParam")
}
  

