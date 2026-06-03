package org.jetbrains.plugins.scala
package codeInspection
package collections

class UnitInMapTest extends OperationsOnCollectionInspectionTest {

  override val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[UnitInMapInspection]

  override protected lazy val description: String =
    ScalaInspectionBundle.message("expression.unit.return.in.map")

  override protected val hint: String =
    ScalaInspectionBundle.message("use.foreach.instead.of.map")

  def test1(): Unit = {
    doTest(
      s"""
        |{
        |  Seq("1", "2").map { x =>
        |    if (x.startsWith("1")) x
        |    else $START{
        |      val y = x + 2
        |    }$END
        |  }
        |
        |  ()
        |}
      """.stripMargin,
      """
        |{
        |  Seq("1", "2").map { x =>
        |    if (x.startsWith("1")) x
        |    else {
        |      val y = x + 2
        |    }
        |  }
        |
        |  ()
        |}
      """.stripMargin,
      """
        |{
        |  Seq("1", "2").foreach { x =>
        |    if (x.startsWith("1")) x
        |    else {
        |      val y = x + 2
        |    }
        |  }
        |
        |  ()
        |}
      """.stripMargin
    )
  }

  def test2(): Unit = checkTextHasNoErrors(
    "val mapped = Seq(1, 2).map(println(_))"
  )

  def test3(): Unit = checkTextHasNoErrors(
    """
      |val a = {
      |  println("")
      |  Seq(1, 2).map(println(_))
      |}
    """.stripMargin
  )

  def test4(): Unit = checkTextHasError(
    s"""
       |{
       |  Seq(1, 2).map(${START}println$END)
       |  3
       |}
     """.stripMargin
  )

  def test_Unit(): Unit = checkTextHasError(
    s"""
       |{
       |  Seq(1).map(_ => ${START}Unit$END)
       |  3
       |}
     """.stripMargin
  )

  def test_SCL15417(): Unit = checkTextHasNoErrors(
      """
        |val o:Option[Int] = ???
        |o.map{ case i => () }.getOrElse{???}
      """.stripMargin
  )

  def testFunctionToFunctionToUnit(): Unit = checkTextHasNoErrors(
    "Seq(1, 2).map(x => () => println(x))"
  )
}
