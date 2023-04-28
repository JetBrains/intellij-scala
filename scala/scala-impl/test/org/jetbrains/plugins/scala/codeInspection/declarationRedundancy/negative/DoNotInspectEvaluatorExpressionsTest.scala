package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.{ScalaAccessCanBeTightenedInspection, ScalaUnusedDeclarationInspection}
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.junit.Assert.assertTrue

import scala.jdk.CollectionConverters.CollectionHasAsScala

class DoNotInspectEvaluatorExpressionsTest extends ScalaLightCodeInsightFixtureTestCase {

  def test_unused_declarations(): Unit = {
    val fragment = ScalaCodeFragment.create("class DoNotInspectEvaluatorExpressionsTest", ScalaLanguage.INSTANCE)(getProject)
    myFixture.openFileInEditor(fragment.getVirtualFile)
    myFixture.enableInspections(classOf[ScalaUnusedDeclarationInspection])
    val relevantHighlights = myFixture.doHighlighting().asScala
      .filter(_.getDescription == ScalaUnusedDeclarationInspection.annotationDescription)
    assertTrue(relevantHighlights.isEmpty)
  }

  def test_can_be_private(): Unit = {
    val code = "class DoNotInspectEvaluatorExpressionsTest { val doNotInspectMe = 42; println(doNotInspectMe) }"
    val fragment = ScalaCodeFragment.create(code, ScalaLanguage.INSTANCE)(getProject)
    myFixture.openFileInEditor(fragment.getVirtualFile)
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
    val relevantHighlights = myFixture.doHighlighting().asScala
      .filter(_.getDescription == ScalaInspectionBundle.message("access.can.be.private"))
    assertTrue(relevantHighlights.isEmpty)
  }
}
