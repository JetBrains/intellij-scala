package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 2014-05-08
 */
class MapGetGetTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("get.get.hint")
  override val inspectionClass = classOf[MapGetGetInspection]

  def test_1() {
    val selected = s"""Map("a"->"x").${START}get("a").get$END"""
    check(selected)
    val text = """Map("a" -> "x").get("a").get"""
    val result = """Map("a" -> "x")("a")"""
    testFix(text, result, hint)
  }

  def test_2() {
    val selected =
      s"""val m = Map("a" -> "b")
         |m.${START}get("a").get$END""".stripMargin
    check(selected)
    val text =
      s"""val m = Map("a" -> "b")
          |m.get("a").get""".stripMargin
    val result =
      s"""val m = Map("a" -> "b")
          |m("a")""".stripMargin
    testFix(text, result, hint)
  }

  def test_3() {
    val selected =
      s"""val m = Map(1 -> "b")
          |m.${START}get(1).get$END""".stripMargin
    check(selected)
    val text =
      s"""val m = Map(1 -> "b")
          |m.get(1).get""".stripMargin
    val result =
      s"""val m = Map(1 -> "b")
          |m(1)""".stripMargin
    testFix(text, result, hint)
  }

  def test_4() {
    val selected = s"""Map("a"->"x").${START}get(0).get$END"""
    check(selected)
    val text = """Map("a" -> "x").get(0).get"""
    val result = """Map("a" -> "x")(0)"""
    testFix(text, result, hint)
  }
}
