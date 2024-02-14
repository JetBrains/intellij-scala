package org.jetbrains.plugins.scala.lang.dataFlow.impl.reachingDefs

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs._
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

import scala.util.Sorting

class ReachingDefinitionsCollectorTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def getBasePath: String = TestUtils.getTestDataPath + "/dataFlow/reachingDefsCollect/"

  override def setUp(): Unit = {
    super.setUp()
    myFixture.setTestDataPath(getBasePath)
  }

  def readTestData: (String, String) = {
    val Seq(before, after) =  TestUtils.readInput(s"$getBasePath${getTestName(true)}.test")
    (before, after)
  }

  def doTest(): Unit = {
    val (input, output) = readTestData

    val file = myFixture.configureByText(ScalaFileType.INSTANCE, input).asInstanceOf[ScalaFile]
    val selection = getSelection(file)
    val startElement = file.findElementAt(selection.getStartOffset)
    val endElement = file.findElementAt(selection.getEndOffset)

    val selectedElements = ScalaPsiUtil.getElementsRange(startElement, endElement)
    val scope: ScalaPsiElement = {
      val commonParent = PsiTreeUtil.findCommonParent(startElement, endElement)
      val cfowner = PsiTreeUtil.getParentOfType(commonParent, classOf[ScControlFlowOwner], false)
      cfowner.getParent.asInstanceOf[ScalaPsiElement]
    }

    val infos = ReachingDefinitionsCollector.collectVariableInfo(selectedElements, scope)
    val actualCfOutput = dumpDefInfos(infos)
    Assert.assertEquals(output.trim, actualCfOutput.trim)
  }

  private def getSelection(file: ScalaFile): TextRange = {
    val model = myFixture.getEditor.getSelectionModel
    if (model.hasSelection) {
      TextRange.create(model.getSelectionStart, model.getSelectionEnd - 1)
    } else{
      TextRange.create(0, file.getTextLength - 1)
    }
  }

  protected def dumpDefInfos(infos: FragmentVariableInfos): String = {
    def variablesText(info: Iterable[VariableInfo]): String = {
      val elementStrings = info.map(_.element.toString).toSeq
      Sorting.stableSort(elementStrings).mkString("\n")
    }

    val inputElements = variablesText(infos.inputVariables)
    val outputElements = variablesText(infos.outputVariables)
    s"""INPUT:
       |$inputElements
       |OUTPUT:
       |$outputElements""".stripMargin.replace("\r", "")
  }

  def testSimpleFragment(): Unit = doTest()

  def testClosure1(): Unit = doTest()
}