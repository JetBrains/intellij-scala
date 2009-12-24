package org.jetbrains.plugins.scala.lang.dataFlow

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import junit.framework.Assert
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.ScalaControlFlowBuilder
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaFile}
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.openapi.editor.SelectionModel
import org.jetbrains.plugins.scala.lang.psi.dataFlow.DfaEngine
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefinitions
import collection.immutable.Set
import collection.mutable.Map

/**
 * @author ilyas
 */

class ReachingDefsTeset extends LightCodeInsightFixtureTestCase {
  protected override def getBasePath = TestUtils.getTestDataPath + "/dataFlow/reachingDefs/"

  override def setUp = {
    super.setUp
    myFixture.setTestDataPath(getBasePath)
  }

  def doTest {
    val input: java.util.List[String] = TestUtils.readInput(getBasePath + getTestName(true) + ".test")
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE, input.get(0))
    val file: ScalaFile = myFixture.getFile.asInstanceOf[ScalaFile]
    val model: SelectionModel = myFixture.getEditor.getSelectionModel
    val start: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionStart else 0)
    val end: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionEnd - 1 else file.getTextLength - 1)
    val owner: ScControlFlowOwner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), classOf[ScControlFlowOwner], false)
    val builder: ScalaControlFlowBuilder = new ScalaControlFlowBuilder(null, null)
    val instructions = builder.buildControlflow(owner)

    import ReachingDefinitions._

    val engine = new DfaEngine(instructions, new ReachingDefinitionsInstance, new ReachingDefinitionsLattice)
    val markup: Map[Instruction, Set[Instruction]] = engine.performDFA

    val cf: String = dumpDataFlow(markup)
    Assert.assertEquals(input.get(1).trim, cf.trim)
  }

  protected def dumpDataFlow(markup: Map[Instruction, Set[Instruction]]): String = {
    var builder: StringBuilder = new StringBuilder
    for (instruction <- markup.keySet.toSeq.sortBy(_.num)) {
      builder.append(instruction.toString)
      val defs: Set[Instruction] = markup(instruction)

      for (d <- defs.toSeq.sortBy(_.num)) {
        builder.append("\n  ").append(d.toString)
      }
      builder.append("\n")
    }
    return builder.toString
  }

  def testFirst = doTest


}