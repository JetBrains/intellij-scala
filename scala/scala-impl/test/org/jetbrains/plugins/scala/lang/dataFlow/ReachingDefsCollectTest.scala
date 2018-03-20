package org.jetbrains.plugins.scala.lang.dataFlow

import com.intellij.openapi.editor.SelectionModel
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs._
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.ScalaFileType
import org.junit.Assert

import scala.util.Sorting

/**
 * @author ilyas
 */

class ReachingDefsCollectTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected  def getBasePath: String = TestUtils.getTestDataPath + "/dataFlow/reachingDefsCollect/"

  override def setUp() {
    super.setUp()
    myFixture.setTestDataPath(getBasePath)
  }

  def readInput = TestUtils.readInput(getBasePath + getTestName(true) + ".test")

  def doTest() {
    val input = readInput
    myFixture.configureByText(ScalaFileType.INSTANCE, input.get(0))
    val file: ScalaFile = myFixture.getFile.asInstanceOf[ScalaFile]
    val model: SelectionModel = myFixture.getEditor.getSelectionModel
    val start: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionStart else 0)
    val end: PsiElement = file.findElementAt(if (model.hasSelection) model.getSelectionEnd - 1 else file.getTextLength - 1)
    val range = ScalaPsiUtil.getElementsRange(start, end)
    val scope = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end),
      classOf[ScControlFlowOwner], false).getParent.asInstanceOf[ScalaPsiElement]

    import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefintionsCollector._
    val infos = collectVariableInfo(range, scope)
    val cf = dumpDefInfos(infos)
    Assert.assertEquals(input.get(1).trim, cf.trim)
  }

  protected def dumpDefInfos(infos: FragmentVariableInfos): String = {
    def variablesText(info: Iterable[VariableInfo]): String =
      Sorting.stableSort(info.map(p => p.element.toString).toSeq).mkString("\n")
    val inputElements = variablesText(infos.inputVariables)
    val outputElements = variablesText(infos.outputVariables)
    s"""INPUT:
       |$inputElements
       |OUTPUT:
       |$outputElements""".stripMargin.replace("\r", "")
  }

  def testSimpleFragment() {
    doTest()
  }
  def testClosure1() {
    doTest()
  }


}