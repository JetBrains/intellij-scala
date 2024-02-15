package org.jetbrains.plugins.scala.lang.controlFlow


import com.intellij.openapi.editor.SelectionModel
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

class ControlFlowTest extends LightJavaCodeInsightFixtureTestCase {
  protected override def getBasePath = TestUtils.getTestDataPath + "/controlFlow/"

  override def setUp(): Unit = {
    TestUtils.optimizeSearchingForIndexableFiles()
    super.setUp()
    myFixture.setTestDataPath(getBasePath)
  }

  def doTest(): Unit = {
    val Seq(before, after) = TestUtils.readInput(getBasePath + getTestName(true) + ".test")
    myFixture.configureByText(ScalaFileType.INSTANCE, before)
    val file: ScalaFile = myFixture.getFile.asInstanceOf[ScalaFile]
    val model: SelectionModel = myFixture.getEditor.getSelectionModel
    val start: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionStart else 0)
    val end: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionEnd - 1 else file.getTextLength - 1)
    val owner: ScControlFlowOwner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), classOf[ScControlFlowOwner], false)
    val instructions = owner.getControlFlow
    val cf: String = dumpControlFlow(instructions.toSeq)
    Assert.assertEquals(after.trim, cf.trim)
  }

  protected def dumpControlFlow(instructions: Seq[Instruction]) = instructions.mkString("\n")

  def testAssignment(): Unit = doTest()
  def testIfStatement(): Unit = doTest()
  def testIfStatement2(): Unit = doTest()
  def testWhile(): Unit = doTest()
  def testWhile2(): Unit = doTest()
  def testMatch1(): Unit = doTest()
  def testFor1(): Unit = doTest()
  def testFor2(): Unit = doTest()
  def testDoWhile1(): Unit = doTest()
  def testReturn1(): Unit = doTest()
  def testMethod1(): Unit = doTest()
  def testThrow1(): Unit = doTest()
  def testKaplan_1703(): Unit = doTest()
  def testKaplan_1703_2(): Unit = doTest()
  def testTry1(): Unit = doTest()
  def testTry2(): Unit = doTest()
  def testTry3(): Unit = doTest()
  def testTryThrow(): Unit = doTest()
  def testNoneThrow(): Unit = doTest()
  def testScl_7393(): Unit = doTest()
  def testUnresolvedParamThrow(): Unit = doTest()
  def testBlockArgByValue(): Unit = doTest()
  def testBlockArgByName(): Unit = doTest()
  def testBlockArgUnknown(): Unit = doTest()
}

