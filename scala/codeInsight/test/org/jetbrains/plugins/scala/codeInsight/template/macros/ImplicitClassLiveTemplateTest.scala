package org.jetbrains.plugins.scala.codeInsight.template.macros

import org.jetbrains.plugins.scala.codeInsight.template.macros.ImplicitClassLiveTemplateTest._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class ImplicitClassLiveTemplateTest extends ScalaLiveTemplateTestBase {

  override protected def templateName = "imc"

  //Using `.sc` (Worksheet) because it's easier to test in them - `imc` live template is available in top-level code in worksheets
  override protected def fileExtension = "sc"

  private def expectedResult(implicitClassName: String, fieldName: String, typeName: String): String =
    s"implicit class $implicitClassName(private val $fieldName: $typeName) extends AnyVal {\n  \n}"

  def testWithDefaultParameters(): Unit = {
    val before = s"$CARET"
    val after  = expectedResult("AnyOps", "value", "Any")
    doTest(before, after)
  }

  def testWithDefaultParametersInFileWithSpaces(): Unit = {
    val before =
      s"""
         |
         |$CARET
         |
         |""".stripMargin
    val after =
      s"""
        |
        |${expectedResult("AnyOps", "value", "Any")}
        |
        |""".stripMargin
    doTest(before, after)
  }

  def testString(): Unit = {
    val before     = s"$CARET"
    val after      = expectedResult("StringOps", "str", "String")
    val parameters = Map("TYPE_NAME" -> "String")
    doTest(before, after, parameters)
  }

  def testSeq_Generic(): Unit = {
    val before     = s"$CARET"
    val after      = expectedResult("SeqOps[T]", "seq", "Seq[T]")
    val parameters = Map("TYPE_NAME" -> "Seq[T]")
    doTest(before, after, parameters)
  }

  def testSeq_Concrete(): Unit = {
    val before     = s"$CARET"
    val after      = expectedResult("SeqOps", "strings", "Seq[String]")
    val parameters = Map("TYPE_NAME" -> "Seq[String]")
    doTest(before, after, parameters)
  }

  def test_it_should_add_generic_type_params_to_type_args_of_implicit_class(): Unit = {
    val before     =
      s"""class Example[A, B, C]
         |object Example {
         |  $CARET
         |}
         |""".stripMargin
    val after      =
      s"""class Example[A, B, C]
         |object Example {
         |  ${expectedResult("ExampleOps[T1, T2]", "value", "Example[T1, String, T2]").indented(2)}
         |}
         |""".stripMargin
    val parameters = Map("TYPE_NAME" -> "Example[T1, String, T2]")
    doTest(before, after, parameters)
  }

  def test_should_be_applicable_in_object(): Unit = assertIsApplicable {
    s"""object A1 {
       |  $CARET
       |}
       |""".stripMargin
  }

  def test_should_be_applicable_in_nested_object(): Unit = assertIsApplicable {
    s"""object O1 {
       |  object O2 {
       |    object O3 {
       |      $CARET
       |    }
       |  }
       |}
       |""".stripMargin
  }

  def test_should_be_applicable_in_package_object(): Unit = assertIsApplicable {
    s"""package object PO {
       |  $CARET
       |}
       |""".stripMargin
  }

  def test_should_be_applicable_in_nested_object_in_package_object(): Unit = assertIsApplicable {
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

  def test_should_be_applicable_at_top_level_in_worksheet(): Unit = assertIsApplicable(
    s"$CARET",
    "sc"
  )

  def test_should_not_be_applicable_at_top_level_in_common_scala_file(): Unit = assertIsNotApplicable(
    s"$CARET",
    "scala"
  )

  def test_should_not_be_applicable_at_top_level_in_common_scala_file_1(): Unit = assertIsNotApplicable(
    s"""class A(){
       |}
       |$CARET""".stripMargin,
    "scala"
  )

  protected def setSettings(prefix: String, suffix: String): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(getProject)
    settings.IMPLICIT_VALUE_CLASS_PREFIX = prefix
    settings.IMPLICIT_VALUE_CLASS_SUFFIX = suffix
  }

  def test_should_use_code_style_settings(): Unit = {
    setSettings("Prefix_", "_Suffix")
    val before = s"$CARET"
    val after = expectedResult("Prefix_Any_Suffix", "value", "Any")
    doTest(before, after)
  }

  def test_should_use_code_style_settings_use_default_suffix(): Unit = {
    setSettings("", "")
    val before = s"$CARET"
    val after = expectedResult("AnyOps", "value", "Any")
    doTest(before, after)
  }

  def test_should_be_applicable_with_package_statements(): Unit = assertIsApplicable {
    s"""package a
       |package b
       |
       |object A1 {
       |  $CARET
       |}
       |""".stripMargin
  }

  def test_should_be_applicable_with_import_statements(): Unit = assertIsApplicable {
    s"""import a.b.c._
       |import q.w.e._
       |
       |object A1 {
       |  $CARET
       |}
       |""".stripMargin
  }

  def test_should_be_applicable_with_package_and_import_statements(): Unit = assertIsApplicable {
    s"""package a
       |package b
       |
       |import a.b.c._
       |import q.w.e._
       |
       |object A1 {
       |  $CARET
       |}
       |""".stripMargin
  }

  def test_do_not_extend_any_val_in_non_statically_available_context(): Unit = {
    val before =
      s"""class Wrapper {
         |$CARET
         |}""".stripMargin
    val after =
      s"""class Wrapper {
         |  implicit class AnyOps(private val value: Any) {
         |    ${""}
         |  }
         |}""".stripMargin
    doTest(before, after)
  }

  def test_do_not_extend_any_val_in_non_statically_available_context_1(): Unit = {
    val before =
      s"""class Wrapper {
         |  def foo = {
         |    {
         |       $CARET
         |    }
         |  }
         |}""".stripMargin
    val after =
      s"""class Wrapper {
         |  def foo = {
         |    {
         |      implicit class AnyOps(private val value: Any) {
         |        ${""}
         |      }
         |    }
         |  }
         |}""".stripMargin
    doTest(before, after)
  }
}

object ImplicitClassLiveTemplateTest {
  implicit class StringExt(private val str: String) extends AnyVal {
    def indented(spaces: Int): String = str.replace("\n", "\n" + " " * spaces)
  }
}
