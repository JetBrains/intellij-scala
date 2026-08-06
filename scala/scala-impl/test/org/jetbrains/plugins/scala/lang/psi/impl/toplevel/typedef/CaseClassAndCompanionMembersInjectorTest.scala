package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.OptionOpsForTest._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.junit.Assert.assertEquals

class CaseClassAndCompanionMembersInjectorTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  def testSCL25718PreservesCompleteAnnotationTextInSyntheticApplyAndCopy(): Unit = {
    configureScalaFromFileText(
      """import scala.annotation.StaticAnnotation
        |import scala.annotation.meta.field
        |
        |class Schema(`type`: String) extends StaticAnnotation
        |
        |case class Foo(
        |  @(Schema @field)(`type` = "boolean")
        |  flag: Boolean
        |)
        |""".stripMargin
    )

    assertSyntheticApplyAndCopyParameterAnnotation("@(Schema @field)(`type` = \"boolean\")")
  }

  def testSCL25718PreservesNonStringAnnotationArgumentsInSyntheticApplyAndCopy(): Unit = {
    configureScalaFromFileText(
      """import scala.annotation.StaticAnnotation
        |
        |class Range(min: Int, enabled: Boolean) extends StaticAnnotation
        |
        |case class Foo(
        |  @Range(min = 1, enabled = true)
        |  value: Int
        |)
        |""".stripMargin
    )

    assertSyntheticApplyAndCopyParameterAnnotation("@Range(min = 1, enabled = true)")
  }

  def testSCL25718PreservesJavaAnnotationTextInSyntheticApplyAndCopy(): Unit = {
    myFixture.addClass(
      """public @interface Schema {
        |  String type();
        |}
        |""".stripMargin
    )
    configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Foo(
        |  @(Schema @field)(`type` = "boolean")
        |  flag: Boolean
        |)
        |""".stripMargin
    )

    assertSyntheticApplyAndCopyParameterAnnotation("@(Schema @field)(`type` = \"boolean\")")
  }

  private def assertSyntheticApplyAndCopyParameterAnnotation(expected: String): Unit = {
    val caseClass = getFile.elements
      .filterByType[ScClass]
      .find(_.name == "Foo")
      .getOrFail("Can't find case class `Foo`")
    val companion = ScalaPsiUtil.getCompanionModule(caseClass)
      .getOrFail("Can't find synthetic companion for case class `Foo`")

    val applyMethod = companion.syntheticMethods
      .find(_.isApplyMethod)
      .getOrFail("Can't find synthetic `apply` method for case class `Foo`")
    val copyMethod = caseClass.syntheticMethods
      .find(_.name == "copy")
      .getOrFail("Can't find synthetic `copy` method for case class `Foo`")

    assertParameterAnnotationText(applyMethod, expected)
    assertParameterAnnotationText(copyMethod, expected)
  }

  private def assertParameterAnnotationText(method: ScFunction, expected: String): Unit = {
    val actual = method.parameters.flatMap(_.annotations).map(_.getText)
    assertEquals(s"Unexpected parameter annotations in synthetic `${method.name}`", Seq(expected), actual)
  }
}
