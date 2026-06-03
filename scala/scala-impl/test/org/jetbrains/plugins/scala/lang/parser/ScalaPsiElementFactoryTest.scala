package org.jetbrains.plugins.scala.lang.parser

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.util.ModificationTrackerTester
import org.junit.Assert

class ScalaPsiElementFactoryTest extends SimpleTestCase {
  def testConstructorWithArgListOnNewLines(): Unit = {
    val methodCall = "Seq(new A)".parse[ScMethodCall]
    val argExpr = methodCall.argumentExpressions.head.asInstanceOf[ScNewTemplateDefinition]
    val place = argExpr.firstConstructorInvocation.get
    val constructorText =
      """Abc(12)
        |(34)""".stripMargin.withNormalizedSeparator

    val constructor = ScalaPsiElementFactory.createConstructorFromText(constructorText, argExpr, place)

    Assert.assertEquals(constructor.getText, constructorText)
  }

  def testCreatingNewSyntheticElementsShouldNotIncrementModificationCounters(): Unit = {
    val scalaFile = fixture.configureByText("dummy.scala", "class Test { def foo(): String = ??? }")
    val modTrackerTester = new ModificationTrackerTester(fixture.getProject)
    val fooMethodElement = scalaFile.elements.filterByType[ScFunction].find(_.name == "foo").get

    import ScalaPsiElementFactory._

    val features = ScalaFeatures.default

    createExpressionFromText("2 + 2", features)
    createWildcardNode(features)
    createIdentifier("name")
    createModifierFromText("final")
    createNewLine()
    createSemicolon
    createEmptyModifierList(fooMethodElement)
    createScalaFileFromText("class MyClass", features)
    createColon
    createWhitespace("  ")
    createScalaDocComment("/** test */")
    createScalaDocMonospaceSyntaxFromText("test")

    modTrackerTester.assertPsiModificationCountNotChanged("creating various synthetic elements")
  }
}
