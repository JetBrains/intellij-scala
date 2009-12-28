package org.jetbrains.plugins.scala.lang.dataFlow

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs._
import org.jetbrains.plugins.scala.util.TestUtils
import junit.framework.Assert
import com.intellij.openapi.editor.SelectionModel
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LightScalaTestCase, ScalaFileType}

/**
 * @author ilyas
 */

class ReachingDefsCollectTest extends LightScalaTestCase {
  override protected  def getBasePath: String = TestUtils.getTestDataPath() + "/dataFlow/reachingDefsCollect/"

  override def setUp = {
    super.setUp
    myFixture.setTestDataPath(getBasePath)
  }

  def readInput = TestUtils.readInput(getBasePath + getTestName(true) + ".test")

  def doTest {
    val input = readInput
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE, input.get(0))
    val file: ScalaFile = myFixture.getFile.asInstanceOf[ScalaFile]
    val model: SelectionModel = myFixture.getEditor.getSelectionModel
    val start: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionStart else 0)
    val end: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionEnd - 1 else file.getTextLength - 1)
    val range = ScalaPsiUtil.getElementsRange(start, end)
    val scope: ScControlFlowOwner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), classOf[ScFunctionDefinition], false)

    import ReachingDefintionsCollector._
    val infos = collectVariableInfo(range, scope)
    val cf = dumpDefInfos(infos)
    Assert.assertEquals(input.get(1).trim, cf.trim)
  }

  protected def dumpDefInfos(infos: FragmentVariableInfos): String = {
    var builder: StringBuilder = new StringBuilder
    builder.append("INPUT:")
    for (vi <- infos.inputVariables) {
      val el = vi.element
      builder.append("\n").append(el.toString).append(" : ").append(el.getName)
    }
    builder.append("\nOUTPUT:")
    for (vi <- infos.outputVariables) {
      val el = vi.element
      builder.append("\n").append(el.toString).append(" : ").append(el.getName)
    }
    return builder.toString
  }

  def testSimpleFragment = doTest


}