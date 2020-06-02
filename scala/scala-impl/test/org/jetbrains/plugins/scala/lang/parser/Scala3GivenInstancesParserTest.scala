package org.jetbrains.plugins.scala.lang.parser

class Scala3GivenInstancesParserTest extends SimpleScala3ParserTestBase {

  def test_full(): Unit = checkTree(
    """
      |given Test[T](using Ord[T]) as Ord[Int] {}
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_without_name(): Unit = checkTree(
    """
      |given [T](using Ord[T]) as Ord[Int] {}
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_without_tpye_params(): Unit = checkTree(
    """
      |given Test(using Ord[Int]) as Ord[Double] {}
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_without_params(): Unit = checkTree(
    """
      |given Test[T] as Ord[Int] {}
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_only_type_args(): Unit = checkTree(
    """
      |given [T] as Ord[T] {}
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_only_name(): Unit = checkTree(
    """
      |given Test as Ord[Int] {}
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_plain(): Unit = checkTree(
    """
      |given as Ord[Int] {}
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_without_sig(): Unit = checkTree(
    """
      |given Test {}
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )


  def test_without_sig_and_template_body(): Unit = checkTree(
    """
      |given Test
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  /********************************** with Template body *********************************************/


  def test_full_alias(): Unit = checkTree(
    """
      |given Test[T](using Ord[T]) as Ord[Int] = ???
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_alias_without_name(): Unit = checkTree(
    """
      |given [T](using Ord[T]) as Ord[Int] = ???
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_alias_without_tpye_params(): Unit = checkTree(
    """
      |given Test(using Ord[Int]) as Ord[Double] = ???
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_alias_without_params(): Unit = checkTree(
    """
      |given Test[T] as Ord[Int] = ???
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_alias_only_type_args(): Unit = checkTree(
    """
      |given [T] as Ord[T] = ???
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_alias_only_name(): Unit = checkTree(
    """
      |given Test as Ord[Int] = ???
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_alias_plain(): Unit = checkTree(
    """
      |given as Ord[Int] = ???
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )

  def test_alias_without_sig(): Unit = checkTree(
    """
      |given Test = ???
      |""".stripMargin,
    """
      |
      |""".stripMargin
  )
}
