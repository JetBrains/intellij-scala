package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion

class Scala3SelectableTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_7

  def testSimple(): Unit = doTest(
    s"""
      |object Blub extends Selectable {
      |  type Fields = (field: Int)
      |}
      |${START}Blub.field$END
      |//Int
      |""".stripMargin
  )

  def testSimpleGeneric(): Unit = doTest(
    s"""
       |class Blub[T] extends Selectable {
       |  type Fields = (field: T)
       |}
       |val blub = new Blub[Boolean]
       |${START}blub.field$END
       |//Boolean
       |""".stripMargin
  )

  def testFullGeneric(): Unit = doTest(
    s"""
       |class Blub[T] extends Selectable {
       |  type Fields = T
       |}
       |val blub = new Blub[(field: Float)]
       |${START}blub.field$END
       |//Float
       |""".stripMargin
  )

  def testMap(): Unit = doTest(
    s"""
       |class Access[T] extends Selectable {
       |  type Fields = NamedTuple.Map[NamedTuple.From[T], Option]
       |}
       |case class Person(name: String, age: Int)
       |val blub = new Access[Person]
       |blub.age
       |${START}(blub.name, blub.age)$END
       |//(Option[String], Option[Int])
       |""".stripMargin
  )

  def testBehindTypeAlias(): Unit = doTest(
    s"""
       |class Blub[T] extends Selectable {
       |  type MkFields[X] = (field: X)
       |  type Fields = MkFields[T]
       |}
       |val blub = new Blub[Boolean]
       |${START}blub.field$END
       |//Boolean
       |""".stripMargin
  )

  def testFieldsBound(): Unit = doTest(
    s"""
       |
       |trait Blub[T] extends Selectable {
       |  type Fields <: (field: T)
       |}
       |val blub: Blub[Boolean] = ???
       |${START}blub.field$END
       |//Boolean
       |""".stripMargin
  )

  def testInGenericContext(): Unit = doTest(
    s"""
       |trait Blub[T] extends Selectable {
       |  type Fields <: (field: T)
       |
       |  def test: T = {
       |    ${START}this.field$END
       |  }
       |}
       |//T
       |""".stripMargin
  )

  // SCL-23425
  def testExample(): Unit = checkTextHasNoErrors(
    s"""
       |trait Expr[Result] extends Selectable:
       |  type Fields = NamedTuple.Map[NamedTuple.From[Result], Expr]
       |  def selectDynamic(fieldName: String) = Expr.Select(this, fieldName)
       |
       |private object Expr {
       |  case class Select[T](parent: Expr[T], name: String)
       |}
       |
       |object Test {
       |  case class Person(name: String, age: Int)
       |  val expr: Expr[Person] = new Expr[Person] {}
       |  expr.name
       |  expr.age
       |}
       |""".stripMargin
  )

  //SCL-24225
  def testMappedNamedTuple(): Unit = checkTextHasNoErrors(
    """
      |import scala.NamedTuple.*
      |
      |class MappedNamedTuple[NT <: AnyNamedTuple](nt: NT) extends Selectable{
      |  type Fields = NamedTuple.Map[NT, Option]
      |  def selectDynamic(name: String): Any =
      |    ???
      |}
      |
      |type Example = (
      |  name: String,
      |  age: Int,
      |)
      |
      |val example: Example = (
      |  name = "Name",
      |  age = 21
      |)
      |
      |val mappedNamedTuple = MappedNamedTuple[Example](example)
      |
      |val name: Option[String] = mappedNamedTuple.name
      |""".stripMargin
  )
}
