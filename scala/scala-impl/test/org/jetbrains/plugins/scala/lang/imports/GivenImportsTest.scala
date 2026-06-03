package org.jetbrains.plugins.scala.lang.imports

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase._

class GivenImportsTest extends SimpleResolveTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  private def replaceImportExprWithSelector(text: String): String =
    text.replaceAll(raw"(import .+)\.([^{.\n]+)", "$1.{$2}")

  private def alsoInSelector(test: String => Unit)(text: String): Unit = {
    test(text)
    test(replaceImportExprWithSelector(text))
  }

  def test_wildcard_does_not_import_givens(): Unit = alsoInSelector(testNoResolve(_))(
    s"""
      |object Source {
      |  given Int = 0
      |}
      |
      |object Target {
      |  import Source.*
      |  given${REFSRC}_Int
      |}
      |""".stripMargin
  )

  def test_wildcard_does_import_implicits(): Unit = alsoInSelector(doResolveTest(_))(
    s"""
       |object Source {
       |  implicit val ${REFTGT}given_Int: Int = 0
       |}
       |
       |object Target {
       |  import Source.*
       |  ${REFSRC}given_Int
       |}
       |""".stripMargin
  )

  def test_wildcard_given_import(): Unit = alsoInSelector(doResolveTest(_))(
    s"""
       |object Source {
       |  ${REFTGT}given Int = 0
       |}
       |
       |object Target {
       |  import Source.given
       |  ${REFSRC}given_Int
       |}
       |""".stripMargin
  )

  def test_wildcard_given_import_does_import_implicit(): Unit = alsoInSelector(doResolveTest(_))(
    s"""
       |object Source {
       |  implicit val ${REFTGT}given_Int: Int = 0
       |}
       |
       |object Target {
       |  import Source.given
       |  ${REFSRC}given_Int
       |}
       |""".stripMargin
  )

  def test_filtered_given_import_does_import_wanted_type(): Unit = alsoInSelector(doResolveTest(_))(
    s"""
       |object Source {
       |  ${REFTGT}given Int = 0
       |  given Short = 1
       |}
       |
       |object Target {
       |  import Source.given Int
       |  ${REFSRC}given_Int
       |}
       |""".stripMargin
  )

  def test_filtered_given_import_does_import_wanted_implicit_type(): Unit = alsoInSelector(doResolveTest(_))(
    s"""
       |object Source {
       |  implicit val ${REFTGT}given_Int: Int = 0
       |  implicit val given_Short: Short = 0
       |}
       |
       |object Target {
       |  import Source.given Int
       |  ${REFSRC}given_Int
       |}
       |""".stripMargin
  )

  def test_filtered_given_import_does_not_import_other_type(): Unit = alsoInSelector(testNoResolve(_))(
    s"""
       |object Source {
       |  given Int = 0
       |  given Short = 1
       |}
       |
       |object Target {
       |  import Source.given Short
       |  ${REFSRC}given_Int
       |}
       |""".stripMargin
  )

  def test_filtered_given_import_does_not_import_other_implicit_type(): Unit = alsoInSelector(testNoResolve(_))(
    s"""
       |object Source {
       |  implicit val given_Int: Int = 0
       |  implicit val given_Short: Short = 0
       |}
       |
       |object Target {
       |  import Source.given Short
       |  ${REFSRC}given_Int
       |}
       |""".stripMargin
  )


  def test_given_from_class(): Unit = alsoInSelector(doResolveTest(_))(
    s"""
       |class Source {
       |  ${REFTGT}given Int = 0
       |}
       |
       |object Target {
       |  val source = new Source
       |  import source.given
       |
       |  ${REFSRC}given_Int
       |}
       |""".stripMargin
  )

  def test_given_from_generic_class(): Unit = alsoInSelector(doResolveTest(_))(
    s"""
       |class Source[T] {
       |  ${REFTGT}given T = ???
       |}
       |
       |def test(source: Source[Int]): Unit = {
       |  import source.given
       |
       |  ${REFSRC}given_T
       |}
       |""".stripMargin
  )

  def test_given_from_generic_class_correct_filtered_type(): Unit = alsoInSelector(doResolveTest(_))(
    s"""
       |class Source[T] {
       |  ${REFTGT}given T = ???
       |}
       |
       |def test(source: Source[Int]): Unit = {
       |  import source.given Int
       |
       |  ${REFSRC}given_T
       |}
       |""".stripMargin
  )

  def test_given_from_generic_class_wrong_filtered_type(): Unit = alsoInSelector(testNoResolve(_))(
    s"""
       |class Source[T] {
       |  given T = ???
       |}
       |
       |def test(source: Source[Int]): Unit = {
       |  import source.given Short
       |
       |  ${REFSRC}given_T
       |}
       |""".stripMargin
  )

  def test_given_from_generic_class_wildcard_filtered_type(): Unit = alsoInSelector(doResolveTest(_))(
    s"""
       |trait Seq[T]
       |
       |class Source[T] {
       |  ${REFTGT}given Seq[T] = ???
       |}
       |
       |def test(source: Source[Int]): Unit = {
       |  import source.given Seq[_]
       |
       |  ${REFSRC}given_Seq_T
       |}
       |""".stripMargin
  )

  def test_shadowed_given(): Unit = testNoResolve(
    s"""
       |object Source {
       |  given Int = 0
       |}
       |
       |object Target {
       |  import Source.{given_Int => _, given Int}
       |  ${REFSRC}given_Int
       |}
       |""".stripMargin
  )
}
