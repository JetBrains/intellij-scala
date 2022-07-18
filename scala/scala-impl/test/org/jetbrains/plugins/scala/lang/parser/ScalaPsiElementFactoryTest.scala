package org.jetbrains.plugins.scala
package lang.parser

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.junit.Assert
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
class ScalaPsiElementFactoryTest extends SimpleTestCase {
  def testConstructorWithArgListOnNewLines(): Unit = {
    val methodCall = "Seq(new A)".parse[ScMethodCall]
    val argExpr = methodCall.argumentExpressions.head.asInstanceOf[ScNewTemplateDefinition]
    val place = argExpr.firstConstructorInvocation.get
    val constructorText = """Abc(12)
                            |(34)""".stripMargin.withNormalizedSeparator

    val constructor = ScalaPsiElementFactory.createConstructorFromText(constructorText, argExpr, place)

    Assert.assertEquals(constructor.getText, constructorText)
  }
}
