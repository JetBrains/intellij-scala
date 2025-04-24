package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion

class Scala3NamedTuplesTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_5
  def testConformanceNormalOk(): Unit = checkTextHasNoErrors(
    """
      |val a: (x: Int) = (x = 1)
      |val b: (x: Int, y: Int) = (x = 1, y = 2)
      |""".stripMargin
  )

  def testConformanceLiteralTypeOk(): Unit = checkTextHasNoErrors(
    """
      |val x: (x: 1) = (x = 1)
      |""".stripMargin
  )

  def testConformanceWithDesugaredOk(): Unit = checkTextHasNoErrors(
    """
      |val a: scala.NamedTuple.NamedTuple[("x", "y"), (Int, String)] = (x = 1, y = "hello")
      |val b: (x: Int, y: String) = scala.NamedTuple[("x", "y"), (Int, String)]((1, "hello"))
      |""".stripMargin
  )

  def testExprType(): Unit = doTest(
    s"""
      |val a = $START(x = 1, y = 2)$END
      |//(x: Int, y: Int)
      |""".stripMargin
  )

  def testExprTypeLiteral(): Unit = doTest(
    s"""
       |val a: (x: 1, y: 2) = $START(x = 1, y = 2)$END
       |
       |//(x: 1, y: 2)
       |""".stripMargin
  )

  def testTypeElementType(): Unit = doTest(
    s"""
       |val a: (x: Int, y: String) = ???
       |println(${START}a$END)
       |
       |//(x: Int, y: String)
       |""".stripMargin
  )

  def testSingleComponentPatternInference(): Unit = doTest(
    s"""
       |val (x = value) = (x = 1)
       |${START}value$END
       |
       |//Int
       |""".stripMargin
  )

  def testComponentPatternInference1(): Unit = doTest(
    s"""
       |val (x = value, y = _) = (x = 1, y = "")
       |${START}value$END
       |
       |//Int
       |""".stripMargin
  )

  def testComponentPatternInference2(): Unit = doTest(
    s"""
       |val (x = _, y = value) = (x = 1, y = "")
       |${START}value$END
       |
       |//String
       |""".stripMargin
  )

  def testReversedComponentPatternInference1(): Unit = doTest(
    s"""
       |val (y = _, x = value) = (x = 1, y = "")
       |${START}value$END
       |
       |//Int
       |""".stripMargin
  )

  def testReversedComponentPatternInference2(): Unit = doTest(
    s"""
       |val (y = value, x = _) = (x = 1, y = "")
       |${START}value$END
       |
       |//String
       |""".stripMargin
  )

  def testOmittedComponentPatternInference1(): Unit = doTest(
    s"""
       |val (x = value) = (x = 1, y = "")
       |${START}value$END
       |
       |//Int
       |""".stripMargin
  )

  def testOmittedComponentPatternInference2(): Unit = doTest(
    s"""
       |val (y = value) = (x = 1, y = "")
       |${START}value$END
       |
       |//String
       |""".stripMargin
  )

  def testNamedTupleExprComponents(): Unit = doTest(
    s"""
       |val tuple = (x = 1, y = 2)
       |${START}tuple.x$END
       |//Int
       |""".stripMargin
  )

  def testNamedTupleExprComponents2(): Unit = doTest(
    s"""
       |val tuple = (x = 1, y = "x")
       |${START}tuple.y$END
       |//String
       |""".stripMargin
  )

  def testNamedTupleTypeComponents(): Unit = doTest(
    s"""
       |val tuple: (x: Int, y: String) = ???
       |${START}tuple.x$END
       |//Int
       |""".stripMargin
  )

  def testNamedTupleTypeComponents2(): Unit = doTest(
    s"""
       |val tuple: (x: Int, y: String) = ???
       |${START}tuple.y$END
       |//String
       |""".stripMargin
  )

  // SCL-23328
  def testInCaseClass(): Unit = doTest(
    s"""
       |case class C(p: (x: Range, y: Range), o: (Range, Range)) {
       |  def xp = ${START}p.x.size$END
       |  def xo = o._1.size
       |}
       |//Int
       |""".stripMargin
  )

  def testNamedTupleToNormalTuple(): Unit = doTest(
    s"""
       |val (x, _) = (a = (y = 1), b = 2)
       |${START}x$END
       |
       |//(y: Int)
       |""".stripMargin
  )

  def testSingleComponentTypeInference(): Unit = doTest(
    s"""(a = "") match {
       |  case (a = x) => ${START}x$END
       |}
       |//String
       |""".stripMargin
  )

  def testTypeInference1(): Unit = doTest(
    s"""(a = 1, b = "") match {
       |  case (a = x, b = _) => ${START}x$END
       |}
       |//Int
       |""".stripMargin
  )

  def testTypeInference2(): Unit = doTest(
    s"""(a = 1, b = "") match {
       |  case (a = _, b = x) => ${START}x$END
       |}
       |//String
       |""".stripMargin
  )

  def testReversedTypeInference1(): Unit = doTest(
    s"""(a = 1, b = "") match {
       |  case (b = _, a = x) => ${START}x$END
       |}
       |//Int
       |""".stripMargin
  )

  def testReversedTypeInference2(): Unit = doTest(
    s"""(a = 1, b = "") match {
       |  case (b = x, a = _) => ${START}x$END
       |}
       |//String
       |""".stripMargin
  )

  def testOmittedTypeInference1(): Unit = doTest(
    s"""(a = 1, b = "") match {
       |  case (a = x) => ${START}x$END
       |}
       |//Int
       |""".stripMargin
  )

  def testOmittedTypeInference2(): Unit = doTest(
    s"""(a = 1, b = "") match {
       |  case (b = x) => ${START}x$END
       |}
       |//String
       |""".stripMargin
  )

  def testScrambledTypeInference(): Unit = doTest(
    s"""(a = 1, b = "", c = true) match {
       |  case (c = z, a = x) => $START(x, z)$END
       |}
       |//(Int, Boolean)
       |""".stripMargin
  )

  def testTypeInferenceToNormalTuple(): Unit = doTest(
    s"""(a = 1, b = "") match {
       |  case (_, x) => ${START}x$END
       |}
       |//String
       |""".stripMargin
  )

  def testTypeInferenceAfterNTtoTConversion(): Unit = doTest(
    s"""def takeTup[A, B](t: (A, B)): (A, B) = t
       |val tup = takeTup((a = true, b = 2))
       |${START}tup$END
       |//(Boolean, Int)
       |""".stripMargin
  )

  def testTtoNTConformance(): Unit = doTest(
    s"""def takeTup[A, B](t: (a: A, b: B)): (a: A, b: B) = t
       |val tup = takeTup((true, 2))
       |${START}tup$END
       |//(a: Boolean, b: Int)
       |""".stripMargin
  )
}
