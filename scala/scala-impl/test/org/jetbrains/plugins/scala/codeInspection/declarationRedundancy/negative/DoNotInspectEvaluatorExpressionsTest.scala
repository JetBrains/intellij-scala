package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.{ScalaAccessCanBeTightenedInspection, ScalaAccessCanBeTightenedPass, ScalaUnusedDeclarationInspection, ScalaUnusedDeclarationPass}
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.junit.Assert.assertTrue

class DoNotInspectEvaluatorExpressionsTest extends ScalaLightCodeInsightFixtureTestCase {
  def test_unused_declarations(): Unit = {
    val fragment = ScalaCodeFragment.create("class DoNotInspectEvaluatorExpressionsTest", ScalaLanguage.INSTANCE)(getProject)
    myFixture.configureByText("Foo.scala", "")
    myFixture.enableInspections(classOf[ScalaUnusedDeclarationInspection])
    val pass = new ScalaUnusedDeclarationPass(fragment, Option(getEditor.getDocument))
    pass.doCollectInformation(null)
    assertTrue(pass.getInfos.size() == 0)
  }

  def test_can_be_private(): Unit = {
    val code = "class DoNotInspectEvaluatorExpressionsTest { val doNotInspectMe = 42; println(doNotInspectMe) }"
    val fragment = ScalaCodeFragment.create(code, ScalaLanguage.INSTANCE)(getProject)
    myFixture.configureByText("Foo.scala", "")
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
    val pass = new ScalaAccessCanBeTightenedPass(fragment, Option(getEditor.getDocument))
    pass.doCollectInformation(null)
    assertTrue(pass.getInfos.size() == 0)
  }
}
