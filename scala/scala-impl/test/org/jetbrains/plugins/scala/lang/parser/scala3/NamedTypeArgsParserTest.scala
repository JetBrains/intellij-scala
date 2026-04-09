package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgument
import org.junit.Assert.{assertEquals, assertTrue}

class NamedTypeArgsParserTest extends SimpleScala3ParserTestBase {
  def test_named_type_args_in_method_call(): Unit =
    checkParseErrors(
      """
        |def construct[Elem, Coll[_]](xs: Elem*): Coll[Elem] = ???
        |val xs1 = construct[Coll = List, Elem = Int](1, 2, 3)
        |val xs2 = construct[Coll = List](1, 2, 3)
        |""".stripMargin
    )

  def test_named_type_args_in_method_call_multiline_after_bracket(): Unit =
    checkParseErrors(
      """
        |def construct[Elem, Coll[_]](xs: Elem*): Coll[Elem] = ???
        |val xs = construct[
        |  Coll = List,
        |  Elem = Int
        |](1, 2, 3)
        |""".stripMargin
    )

  def test_named_type_args_in_infix_call(): Unit =
    checkParseErrors(
      """
        |val x = left op [Elem = Int] right
        |""".stripMargin
    )

  def test_named_type_args_in_type_constructor_are_parsed(): Unit =
    checkParseErrors(
      """
        |class C[T]
        |type X = C[T = Int]
        |""".stripMargin
    )

  def test_mixed_positional_and_named_type_args_positional_first(): Unit =
    checkParseErrors(
      s"""
        |construct[Int, ${err("Named and positional type arguments cannot be mixed")}Coll = List](1)
        |""".stripMargin
    )

  def test_mixed_positional_and_named_type_args_named_first(): Unit =
    checkParseErrors(
      s"""
        |construct[Coll = List, ${err("Named and positional type arguments cannot be mixed")}Int](1)
        |""".stripMargin
    )

  def test_mixed_positional_and_named_type_args_named_first_multiline_after_bracket(): Unit =
    checkParseErrors(
      s"""
        |construct[
        |  Coll = List,
        |  ${err("Named and positional type arguments cannot be mixed")}Int
        |](1)
        |""".stripMargin
    )

  def test_named_type_arg_name_is_reference_element(): Unit = {
    val file = checkParseErrors(
      """
        |def construct[Elem, Coll[_]](xs: Elem*): Coll[Elem] = ???
        |val xs = construct[Coll = List, Elem = Int](1, 2, 3)
        |""".stripMargin
    )

    val typeArgs = file.depthFirst().filterByType[ScTypeArgument].toSeq
    assertEquals(Seq("Coll", "Elem"), typeArgs.flatMap(_.name))
    assertTrue(typeArgs.flatMap(_.nameElement).forall(_.getNode.getElementType == ScalaElementType.REFERENCE))
  }
}
