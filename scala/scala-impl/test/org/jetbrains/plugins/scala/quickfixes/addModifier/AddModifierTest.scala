package org.jetbrains.plugins.scala
package quickfixes
package addModifier

import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
 * User: Alefas
 * Date: 20.10.11
 */

class AddModifierTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def testAbstractModifier(): Unit = {
    configureFromFileTextAdapter(
      "dummy.scala",
      """
        |@Deprecated
        |class Foo<caret> extends Runnable
      """.stripMargin
    )

    val place = getFileAdapter.findElementAt(getEditorAdapter.getCaretModel.getOffset)
    place.parentOfType(classOf[ScModifierListOwner]).foreach { owner =>
      inWriteAction(owner.setModifierProperty("abstract"))
    }

    checkResultByText(
      """
        |@Deprecated
        |abstract class Foo<caret> extends Runnable
      """.stripMargin
    )
  }

}