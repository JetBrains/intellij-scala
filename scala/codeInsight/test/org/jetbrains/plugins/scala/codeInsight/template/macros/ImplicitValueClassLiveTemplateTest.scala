package org.jetbrains.plugins.scala.codeInsight.template.macros

import org.jetbrains.plugins.scala.codeInsight.template.macros.ImplicitValueClassLiveTemplateTest._

class ImplicitValueClassLiveTemplateTest extends ScalaLiveTemplateTestBase {

  override protected def templateName = "imvc"

  // easier to test in worksheets due to the allow top level implicit classes
  override protected def fileExtension = "sc"

  private def expectedResult(implicitClassName: String, fieldName: String, typeName: String): String =
    s"implicit class $implicitClassName(private val $fieldName: $typeName) extends AnyVal {\n  \n}"

  def testWithDefaultParameters(): Unit = {
    val before = s"""$CARET""".stripMargin
    val after  = expectedResult("AnyOps", "value", "Any")
    doTest(before, after)
  }

  def testString(): Unit = {
    val before     = s"""$CARET""".stripMargin
    val after      = expectedResult("StringOps", "str", "String")
    val parameters = Map("TYPE_NAME" -> "String")
    doTest(before, after, parameters)
  }

  def testSeq_Generic(): Unit = {
    val before     = s"""$CARET""".stripMargin
    val after      = expectedResult("SeqOps[T]", "seq", "Seq[T]")
    val parameters = Map("TYPE_NAME" -> "Seq[T]")
    doTest(before, after, parameters)
  }

  def testSeq_Concrete(): Unit = {
    val before     = s"""$CARET""".stripMargin
    val after      = expectedResult("SeqOps", "strings", "Seq[String]")
    val parameters = Map("TYPE_NAME" -> "Seq[String]")
    doTest(before, after, parameters)
  }

  def test_it_should_add_generic_type_params_to_type_arts_of_implicit_class(): Unit = {
    val before     =
      s"""class Example[A, B, C]
         |object Example {
         |  $CARET
         |}
         |""".stripMargin
    val after      =
      s"""class Example[A, B, C]
         |object Example {
         |
         |  ${expectedResult("ExampleOps[T1, T2]", "value", "Example[T1, String, T2]").indented(2)}
         |}
         |""".stripMargin
    val parameters = Map("TYPE_NAME" -> "Example[T1, String, T2]")
    doTest(before, after, parameters)
  }

  def should_be_applicable_in_object(): Unit = assertIsApplicable {
    s"""object A1 {
       |  $CARET
       |}
       |""".stripMargin
  }

  def should_be_applicable_in_nested_object(): Unit = assertIsApplicable {
    s"""object O1 {
       |  object O2 {
       |    object O3 {
       |      $CARET
       |    }
       |  }
       |}
       |""".stripMargin
  }

  def should_be_applicable_in_package_object(): Unit = assertIsApplicable {
    s"""package object PO {
       |  $CARET
       |}
       |""".stripMargin
  }

  def should_be_applicable_in_nested_object_in_package_object(): Unit = assertIsApplicable {
    s"""package object PO {
       |  object O1 {
       |    object O2 {
       |      object O3 {
       |        $CARET
       |      }
       |    }
       |  }
       |}
       |""".stripMargin
  }

  def test_should_not_be_applicable_in_non_statically_accessible_context(): Unit = assertIsNotApplicable {
    s"""class C1 {
       |  $CARET
       |}
       |""".stripMargin
  }

  def test_should_not_be_applicable_in_non_statically_accessible_context_1(): Unit = assertIsNotApplicable {
    s"""object O1 {
       |  object O2 {
       |    class C3 {
       |      $CARET
       |    }
       |  }
       |}
       |""".stripMargin
  }

  def test_should_be_applicable_at_top_level_in_worksheet(): Unit = assertIsApplicable(
    s"""$CARET""",
    "sc"
  )

  def test_should_not_be_applicable_at_top_level_in_common_scala_file(): Unit = assertIsNotApplicable(
    s"""$CARET""",
    "scala"
  )

  def test_should_not_be_applicable_at_top_level_in_common_scala_file_1(): Unit = assertIsNotApplicable(
    s"""class A(){
       |}
       |$CARET""".stripMargin,
    "scala"
  )
}

object ImplicitValueClassLiveTemplateTest {

  implicit class StringExt(private val str: String) extends AnyVal {
    def indented(spaces: Int): String = str.replace("\n", "\n" + " " * spaces)
  }
}
