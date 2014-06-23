package org.jetbrains.plugins.scala.lang.controlFlow


import com.intellij.openapi.editor.SelectionModel
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScControlFlowOwner
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.ScalaControlFlowBuilder
import org.jetbrains.plugins.scala.util.TestUtils
import junit.framework.Assert
import com.intellij.testFramework.fixtures.{LightCodeInsightFixtureTestCase}
/**
 * @author ilyas
 */

class ControlFlowTest extends LightCodeInsightFixtureTestCase {
  protected override def getBasePath = TestUtils.getTestDataPath + "/controlFlow/"

  override def setUp() {
    super.setUp()
    myFixture.setTestDataPath(getBasePath)
  }

  def doTest() {
    val input: java.util.List[String] = TestUtils.readInput(getBasePath + getTestName(true) + ".test")
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE, input.get(0))
    val file: ScalaFile = myFixture.getFile.asInstanceOf[ScalaFile]
    val model: SelectionModel = myFixture.getEditor.getSelectionModel
    val start: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionStart else 0)
    val end: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionEnd - 1 else file.getTextLength - 1)
    val owner: ScControlFlowOwner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), classOf[ScControlFlowOwner], false)
    val instructions = owner.getControlFlow(false)
    val cf: String = dumpControlFlow(instructions)
    Assert.assertEquals(input.get(1).trim, cf.trim)
  }

  protected def dumpControlFlow(instructions: Seq[Instruction]) = instructions.mkString("\n")

  def testAssignment() {doTest()}
  def testIfStatement() {doTest()}
  def testIfStatement2() {doTest()}
  def testWhile() {doTest()}
  def testWhile2() {doTest()}
  def testMatch1() {doTest()}
  def testFor1() {doTest()}
  def testFor2() {doTest()}
  def testDoWhile1() {doTest()}
  def testReturn1() {doTest()}
  def testMethod1() {doTest()}
  def testThrow1() {doTest()}
  def testKaplan_1703() {doTest()}
  def testKaplan_1703_2() {doTest()}
  def testTry1() {doTest()}
  def testTry2() {doTest()}
  def testTry3() {doTest()}
  def testNoneThrow() = doTest()

}

