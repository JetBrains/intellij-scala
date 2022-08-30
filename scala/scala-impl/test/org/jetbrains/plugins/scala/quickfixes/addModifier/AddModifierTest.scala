package org.jetbrains.plugins.scala.quickfixes.addModifier

import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.junit.Assert

class AddModifierTest extends ScalaLightCodeInsightFixtureTestCase {

  def testAbstractModifier(): Unit = {
    configureFromFileText(
      "dummy.scala",
      s"@Deprecated class Foo$CARET extends Runnable"
    )

    import org.jetbrains.plugins.scala.extensions._
    val place = getFile.findElementAt(getEditor.getCaretModel.getOffset)
    val owner = PsiTreeUtil.getParentOfType(place, classOf[ScModifierListOwner])
    Assert.assertNotNull(owner)

    CommandProcessor.getInstance.executeCommand(getProject, () => {
      inWriteAction {
        owner.getModifierList.setModifierProperty(ScalaModifier.ABSTRACT, true)
      }
    }, null, null)

    myFixture.checkResult(s"@Deprecated abstract class Foo$CARET extends Runnable")
  }
}