package org.jetbrains.plugins.scala.lang.dataFlow

import com.intellij.openapi.editor.SelectionModel
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.ScalaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.psi.dataFlow.DfaEngine
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

import scala.collection.mutable

class ReachingDefsTest extends LightJavaCodeInsightFixtureTestCase {
  protected override def getBasePath = TestUtils.getTestDataPath + "/dataFlow/reachingDefs/"

  override def setUp(): Unit = {
    super.setUp()
    myFixture.setTestDataPath(getBasePath)
  }

  def doTest(): Unit = {
    val input: java.util.List[String] = TestUtils.readInput(getBasePath + getTestName(true) + ".test")
    myFixture.configureByText(ScalaFileType.INSTANCE, input.get(0))
    val file: ScalaFile = myFixture.getFile.asInstanceOf[ScalaFile]
    val model: SelectionModel = myFixture.getEditor.getSelectionModel
    val start: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionStart else 0)
    val end: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionEnd - 1 else file.getTextLength - 1)
    val owner: ScControlFlowOwner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), classOf[ScControlFlowOwner], false)
    val builder: ScalaControlFlowBuilder = new ScalaControlFlowBuilder(null, null)
    val instructions = builder.buildControlflow(owner)

    import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefinitions._

    val engine = new DfaEngine(instructions, ReachingDefinitionsInstance, ReachingDefinitionsLattice)
    val markup: mutable.Map[Instruction, Set[Instruction]] = engine.performDFA

    val cf: String = dumpDataFlow(markup)
    Assert.assertEquals(input.get(1).trim, cf.trim)
  }

  protected def dumpDataFlow(markup: mutable.Map[Instruction, Set[Instruction]]): String = {
    val builder: StringBuilder = new StringBuilder
    for (instruction <- markup.keySet.toSeq.sortBy(_.num)) {
      builder.append(instruction.toString)
      val defs: Set[Instruction] = markup(instruction)

      for (d <- defs.toSeq.sortBy(_.num)) {
        builder.append("\n  ").append(d.toString)
      }
      builder.append("\n")
    }
    builder.toString
  }

  def testFirst(): Unit = doTest()
  def testSecond(): Unit = doTest()

}