package org.jetbrains.plugins.scala
package codeInspection
package collections

class MapGetGetTest extends OperationsOnCollectionInspectionTest {

  override val hint = ScalaInspectionBundle.message("get.get.hint")
  override val classOfInspection = classOf[MapGetGetInspection]

  def test_1(): Unit = {
    val selected = s"""Map("a"->"x").${START}get("a").get$END"""
    checkTextHasError(selected)
    val text = """Map("a" -> "x").get("a").get"""
    val result = """Map("a" -> "x")("a")"""
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected =
      s"""val m = Map("a" -> "b")
         |m.${START}get("a").get$END""".stripMargin
    checkTextHasError(selected)
    val text =
      s"""val m = Map("a" -> "b")
          |m.get("a").get""".stripMargin
    val result =
      s"""val m = Map("a" -> "b")
          |m("a")""".stripMargin
    testQuickFix(text, result, hint)
  }

  def test_3(): Unit = {
    val selected =
      s"""val m = Map(1 -> "b")
          |m.${START}get(1).get$END""".stripMargin
    checkTextHasError(selected)
    val text =
      s"""val m = Map(1 -> "b")
          |m.get(1).get""".stripMargin
    val result =
      s"""val m = Map(1 -> "b")
          |m(1)""".stripMargin
    testQuickFix(text, result, hint)
  }

  def test_4(): Unit = {
    val selected = s"""Map("a"->"x").${START}get(0).get$END"""
    checkTextHasError(selected)
    val text = """Map("a" -> "x").get(0).get"""
    val result = """Map("a" -> "x")(0)"""
    testQuickFix(text, result, hint)
  }
}
