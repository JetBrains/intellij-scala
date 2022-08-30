package org.jetbrains.plugins.scala.quickfixes.addModifier

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.util.WriteCommandActionEx
import org.junit.Assert

class AddModifierTest extends ScalaLightCodeInsightFixtureTestCase {

  def testAbstractModifier(): Unit = {
    configureFromFileText(
      "dummy.scala",
      s"@Deprecated class Foo$CARET extends Runnable"
    )
    val place = getFile.findElementAt(getEditor.getCaretModel.getOffset)
    val owner = PsiTreeUtil.getParentOfType(place, classOf[ScModifierListOwner])
    Assert.assertNotNull(owner)

    WriteCommandActionEx.runWriteCommandAction(getProject, (() => {
      owner.getModifierList.setModifierProperty(ScalaModifier.ABSTRACT, true)
    }))

    myFixture.checkResult(s"@Deprecated abstract class Foo$CARET extends Runnable")
  }
}