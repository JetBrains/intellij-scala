package org.jetbrains.plugins.scala.lang.parser.scala3

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.junit.Assert.assertNotNull

class TraitParameterParserTest extends SimpleScala3ParserTestBase {
  private def checkHasParserErrors(text: String): Unit = {
    val file = parseText(text)
    val firstError = PsiTreeUtil.findChildOfType(file, classOf[PsiErrorElement])
    assertNotNull("Expected parser errors", firstError)
  }

  def test_without_params(): Unit = checkParseErrors(
    "trait Test"
  )

  def test_one_trait_param(): Unit = checkParseErrors(
    "trait Test(arg: Int)"
  )

  def test_default_trait_param(): Unit = checkParseErrors(
    "trait Test(arg: Int, arg2: Boolean = true)"
  )

  def test_val_trait_param(): Unit = checkParseErrors(
    "trait Test(val member: Int)"
  )

  def test_two_parameter_clauses(): Unit = checkParseErrors(
    "trait Test(arg: Int)(val member: Int)"
  )

  def test_interleaved_type_param_clauses_in_trait_constructor_are_disallowed(): Unit = checkHasParserErrors(
    "trait Test(arg: Int)[A](a: A)"
  )

  def test_interleaved_type_param_clauses_in_class_constructor_are_disallowed(): Unit = checkParseErrors(
    s"class Test(arg: Int)${err("Interleaved type parameter clauses are not supported in constructors")}[A](a: A)"
  )

  def test_with_extends(): Unit = checkParseErrors(
    "trait Test(arg: Int) extends Base"
  )

  def test(): Unit = checkParseErrors(
    """
      |object testindent
      |
      |  class A
      |
      |  /* foo */ class B
      |
      |  class C
      |""".stripMargin
  )
}
