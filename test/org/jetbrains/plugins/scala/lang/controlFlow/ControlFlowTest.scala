package org.jetbrains.plugins.scala.lang.controlFlow


import com.intellij.openapi.editor.SelectionModel
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScControlFlowOwner
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.ScalaControlFlowBuilder
import org.jetbrains.plugins.scala.util.TestUtils
import junit.framework.Assert

/**
 * @author ilyas
 */

class ControlFlowTest extends LightCodeInsightFixtureTestCase {
  protected override def getBasePath = TestUtils.getTestDataPath + "/controlFlow/"

  override def setUp = {
    super.setUp
    myFixture.setTestDataPath(getBasePath)
  }

  def doTest {
    val input: java.util.List[String] = TestUtils.readInput(getTestDataPath + getTestName(true) + ".test")
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE, input.get(0))
    val file: ScalaFile = myFixture.getFile.asInstanceOf[ScalaFile]
    val model: SelectionModel = myFixture.getEditor.getSelectionModel
    val start: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionStart else 0)
    val end: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionEnd - 1 else file.getTextLength - 1)
    val owner: ScControlFlowOwner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), classOf[ScControlFlowOwner], false)
    val builder: ScalaControlFlowBuilder = new ScalaControlFlowBuilder(null, null)
    val instructions = builder.buildControlflow(owner)
    val cf: String = dumpControlFlow(instructions)
    Assert.assertEquals(input.get(1).trim, cf.trim)
  }

  protected  def dumpControlFlow(instructions: Seq[Instruction]): String = {
    var builder: StringBuilder = new StringBuilder
    for (instruction <- instructions) {
      builder.append(instruction.toString).append("\n")
    }
    return builder.toString
  }

  @throws(classOf[Throwable])
  def testAssignment = doTest

}

