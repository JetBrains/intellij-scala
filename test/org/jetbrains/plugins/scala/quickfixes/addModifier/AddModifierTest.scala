package org.jetbrains.plugins.scala.quickfixes.addModifier

import org.jetbrains.plugins.scala.lang.completion3.ScalaLightPlatformCodeInsightTestCaseAdapter
import junit.framework.Assert
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
 * User: Alefas
 * Date: 20.10.11
 */

class AddModifierTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  def doTest(fileText: String, result: String, modifier: String, value: Boolean) {
    configureFromFileTextAdapter("dummy.scala", fileText)
    val place = getFileAdapter.findElementAt(getEditorAdapter.getCaretModel.getOffset)
    val owner = PsiTreeUtil.getParentOfType(place, classOf[ScModifierListOwner])
    assert(owner != null)
    owner.setModifierProperty(modifier, value)
    checkResultByText(result)
  }
  
  def testAbstractModifier() {
    val fileText =
      """
      |@Deprecated
      |class Foo<caret> extends Runnable
      """.stripMargin('|').replaceAll("\r", "").trim()

    val resultText =
      """
      |@Deprecated
      |abstract class Foo<caret> extends Runnable
      """.stripMargin('|').replaceAll("\r", "").trim()
    doTest(fileText, resultText, "abstract", true)
  }
}