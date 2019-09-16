package org.jetbrains.plugins.scala.uast

import org.jetbrains.plugins.scala.lang.psi.uast.utils.OptionExt._
import org.jetbrains.plugins.scala.uast.AbstractUastFixtureTest._
import org.jetbrains.uast.{UAnnotation, UFile, ULiteralExpression}
import org.junit.Assert

import scala.collection.JavaConverters._
import scala.language.postfixOps


class ScalaUastApiTest extends AbstractUastFixtureTest {
  override def check(testName: String, file: UFile): Unit = {}

  def testUastAnchors(): Unit =
    doTest("SimpleClass.scala") { (_, file) =>
      val uClass = file.getClasses.asScala.filter(_.getQualifiedName == "SimpleClass").ensuring(_.size == 1).head
      Assert.assertEquals("SimpleClass", uClass.getUastAnchor ?> (_.getSourcePsi) ?> (_.getText) orNull)

      val uMethod = uClass.getMethods.filter(_.getName == "bar").ensuring(_.length == 1).head
      Assert.assertEquals("bar", uMethod.getUastAnchor ?> (_.getSourcePsi) ?> (_.getText) orNull)

      val uParameter = uMethod.getUastParameters.asScala.filter(_.getName == "param").ensuring(_.length == 1).head
      Assert.assertEquals("param", uParameter.getUastAnchor ?> (_.getSourcePsi) ?> (_.getText) orNull)
    }

  def testAnnotationAnchor(): Unit =
    doTest("SimpleClass.scala") { (_, file) =>
      val uAnnotation = findElementByText[UAnnotation](file, "@java.lang.Deprecated")
      Assert.assertEquals("Deprecated", uAnnotation.getUastAnchor ?> (_.getSourcePsi) ?> (_.getText) orNull)
    }

  def testStringLiteral(): Unit =
    doTest("Annotations.scala") { (_, file) =>
      val literal1 = findElementByTextFromPsi[ULiteralExpression](file, "\"abc\"")
      Assert.assertTrue(literal1.isString)
      Assert.assertEquals("abc", literal1.getValue)

      val literal2 = findElementByTextFromPsi[ULiteralExpression](file, "123")
      Assert.assertFalse(literal2.isString)
      Assert.assertEquals(123, literal2.getValue)
    }
}
