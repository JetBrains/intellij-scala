package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.{PsiAnnotation, PsiAnnotationMemberValue, PsiArrayInitializerMemberValue, PsiParameter}
import org.jetbrains.plugins.scala.OptionOpsForTest._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.junit.Assert.{assertEquals, assertNotNull}

class ScLightModifierListTest extends ScalaLightCodeInsightFixtureTestCase {

  // SCL-25723
  def testGetAnnotation_WithBacktickedNamedArgument(): Unit = {
    myFixture.addClass(
      """public @interface BacktickedSchema {
        |  String type();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Direct(
        |  @(BacktickedSchema @field)(`type` = "boolean")
        |  value: Boolean
        |)
        |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "Direct", "value", "BacktickedSchema")
    assertAnnotationAttributeText(annotation, "type", "\"boolean\"")
  }

  def testGetAnnotation_PreservesBacktickedJavaCompatibleAttributeNames(): Unit = {
    myFixture.addClass(
      """public @interface JavaNameSchema {
        |  String type();
        |  String foo$plus();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class JavaNames(
        |  @(JavaNameSchema @field)(`type` = "escaped", `foo$plus` = "plain")
        |  flag: Boolean
        |)
        |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "JavaNames", "flag", "JavaNameSchema")
    assertAnnotationAttributeText(annotation, "type", "\"escaped\"")
    assertAnnotationAttributeText(annotation, "foo$plus", "\"plain\"")
  }

  def testGetAnnotation_WithLiteralArguments(): Unit = {
    myFixture.addClass(
      """public @interface LiteralSchema {
        |  String type();
        |  int count();
        |  boolean enabled();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Literals(
        |  @(LiteralSchema @field)(`type` = "text", count = 1, enabled = true)
        |  flag: Boolean
        |)
        |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "Literals", "flag", "LiteralSchema")
    assertAnnotationAttributeText(annotation, "type", "\"text\"")
    assertAnnotationAttributeText(annotation, "count", "1")
    assertAnnotationAttributeText(annotation, "enabled", "true")
  }

  def testGetAnnotation_WithMultilineStringArgument(): Unit = {
    myFixture.addClass(
      """public @interface MultilineSchema {
        |  String type();
        |}
        |""".stripMargin
    )
    val multilineString = "\"\"\"line one\nline two\"\"\""
    val file = configureScalaFromFileText(
      s"""import scala.annotation.meta.field
         |
         |case class Multiline(
         |  @(MultilineSchema @field)(`type` = $multilineString)
         |  flag: Boolean
         |)
         |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "Multiline", "flag", "MultilineSchema")
    assertAnnotationAttributeText(annotation, "type", "\"line one\\nline two\"")
  }

  def testGetAnnotation_WithArrayArgument(): Unit = {
    myFixture.addClass(
      """public @interface ArraySchema {
        |  String[] type();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Arrays(
        |  @(ArraySchema @field)(`type` = Array("first", "second"))
        |  flag: Boolean
        |)
        |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "Arrays", "flag", "ArraySchema")
    val value = annotationAttribute(annotation, "type").asInstanceOf[PsiArrayInitializerMemberValue]
    assertEquals(Seq("\"first\"", "\"second\""), value.getInitializers.toSeq.map(_.getText))
  }

  def testGetAnnotation_WithClassOfArgument(): Unit = {
    myFixture.addClass(
      """public @interface ClassSchema {
        |  Class<?> type();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Classes(
        |  @(ClassSchema @field)(`type` = classOf[String])
        |  flag: Boolean
        |)
        |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "Classes", "flag", "ClassSchema")
    assertAnnotationAttributeText(annotation, "type", "java.lang.String.class")
  }

  def testGetAnnotation_WithNestedAnnotationArgument(): Unit = {
    myFixture.addClass(
      """public @interface NestedInner {
        |  String type();
        |}
        |""".stripMargin
    )
    myFixture.addClass(
      """public @interface NestedOuter {
        |  NestedInner nested();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Nested(
        |  @(NestedOuter @field)(nested = new NestedInner(`type` = "nested"))
        |  flag: Boolean
        |)
        |""".stripMargin
    )
    val outer = getAnnotationForParameter(file, "Nested", "flag", "NestedOuter")

    val inner = annotationAttribute(outer, "nested").asInstanceOf[PsiAnnotation]
    assertAnnotationAttributeText(inner, "type", "\"nested\"")
  }

  private def assertAnnotationAttributeText(annotation: PsiAnnotation, name: String, expectedText: String): Unit =
    assertEquals(expectedText, annotationAttribute(annotation, name).getText)

  private def annotationAttribute(annotation: PsiAnnotation, name: String): PsiAnnotationMemberValue = {
    val value = annotation.findDeclaredAttributeValue(name)
    assertNotNull(s"Java light PSI must expose the `$name` annotation attribute", value)
    value
  }

  private def getAnnotationForParameter(
    scalaFile: ScalaFile,
    className: String,
    parameterName: String,
    annotationName: String
  ): PsiAnnotation = {
    val caseClass = findCaseClass(scalaFile, className)
    val parameter = findConstructorParameter(caseClass, parameterName)
    val annotation = parameter.getAnnotation(annotationName)
    assertNotNull(s"Java light PSI must expose the @$annotationName annotation", annotation)
    annotation
  }

  private def findCaseClass(scalaFile: ScalaFile, className: String): ScClass = {
    val classes = scalaFile.typeDefinitions.filterByType[ScClass]
    val caseClass = classes.find(_.name == className)
    caseClass.getOrFail(s"Can't find case class `$className`")
  }

  private def findConstructorParameter(caseClass: ScClass, parameterName: String): PsiParameter = {
    val constructors = caseClass.getConstructors
    val parameters = constructors.iterator.flatMap(_.getParameterList.getParameters.iterator)
    val parameter = parameters.find(_.name == parameterName)
    parameter.getOrFail(s"Can't find constructor parameter `$parameterName`")
  }
}
