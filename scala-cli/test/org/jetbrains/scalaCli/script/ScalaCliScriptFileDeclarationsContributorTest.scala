package org.jetbrains.scalaCli.script

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert.assertNotNull

class ScalaCliScriptFileDeclarationsContributorTest extends ScalaLightCodeInsightFixtureTestCase {

  def testArgsResolveInScript(): Unit = {
    myFixture.configureByText("script.sc", "val first = ar<caret>gs.headOption")

    val reference = myFixture.getFile.findReferenceAt(myFixture.getCaretOffset)
    assertNotNull("Scala CLI script args should resolve", reference)
    assertNotNull("Scala CLI script args should resolve", reference.resolve())
  }
}
