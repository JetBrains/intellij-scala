package org.jetbrains.plugins.scala.lang.typeInference.shims

import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class TypeIntrinsicsTestBase extends ScalaLightCodeInsightFixtureTestCase {
  def assertTypeIs(code: String, tpe: String): Unit = {
    val file = ScalaPsiElementFactory.createScalaFileFromText(transformCode(code), ScalaFeatures.onlyByVersion(version))(getProject)
    val typeElement = file.getLastChild.getLastChild.asInstanceOf[ScTypeElement]
    val actual = typeElement.`type`().toOption.fold("<error>")(_.presentableText(TypePresentationContext(typeElement)))
    assertEquals(tpe, actual)
  }

  protected def transformCode(code: String): String = code

  def assertConforms(code: String, tpe: String): Unit = {
    val expected = {
      val file = ScalaPsiElementFactory.createScalaFileFromText(s"type X = $tpe", ScalaFeatures.onlyByVersion(version))(getProject)
      val typeElement = file.getLastChild.getLastChild.asInstanceOf[ScTypeElement]
      typeElement.`type`().get
    }

    val actual = {
      val file = ScalaPsiElementFactory.createScalaFileFromText(code, ScalaFeatures.onlyByVersion(version))(getProject)
      val typeElement = file.getLastChild.getLastChild.asInstanceOf[ScTypeElement]
      typeElement.`type`().get
    }

    assert(actual.conforms(expected), s"$actual does not conform to $expected")
  }
}
