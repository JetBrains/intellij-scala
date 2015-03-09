package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class FoldTrueAndTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("fold.true.and.hint")
  def test_1() {
    val selected = s"List(false).${START}foldLeft(true){_ && _}$END"
    check(selected)
    val text = "List(false).foldLeft(true){_ && _}"
    val result = "List(false).forall(_)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"""def a(x: String) = false
                     |List("a").$START/:(true) (_ && a(_))$END""".stripMargin
    check(selected)
    val text = """def a(x: String) = false
                 |List("a")./:(true) (_ && a(_))""".stripMargin
    val result = """def a(x: String) = false
                   |List("a").forall(a(_))""".stripMargin
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"""def a(x: String) = false
                     |List("a").${START}fold(true) ((x,y) => x && a(y))$END""".stripMargin
    check(selected)
    val text = """def a(x: String) = false
                 |List("a").fold(true) ((x,y) => x && a(y))""".stripMargin
    val result = """def a(x: String) = false
                   |List("a").forall(y => a(y))""".stripMargin
    testFix(text, result, hint)
  }

  def test_4() {

    val text = """def a(x: String) = false
                 |List("a").foldLeft(true) ((x,y) => x && a(x))""".stripMargin
    checkTextHasNoErrors(text, hint, inspectionClass)
  }

  def testWithoutSideEffect(): Unit = {
    doTest(
      s"""
         |List(0).${START}foldLeft(true) {(x, y) =>
         |  x && {
         |    var z = 1
         |    z += 1
         |    z + y % 2 == 1
         |  }
         |}$END
       """.stripMargin,
      """
         |List(0).foldLeft(true) {(x, y) =>
         |  x && {
         |    var z = 1
         |    z += 1
         |    z + y % 2 == 1
         |  }
         |}
       """.stripMargin,
      """
        |List(0).forall(y => {
        |  var z = 1
        |  z += 1
        |  z + y % 2 == 1
        |})
      """.stripMargin)
  }

  def testWithSideEffect(): Unit = {
    checkTextHasNoErrors(
      """
        |var q = 1
        |List(0).foldLeft(true) {(x, y) =>
        |  x && {
        |    var z = 1
        |    q += 1
        |    z + y % 2 == 1
        |  }
        |}
      """.stripMargin)
  }

  override val inspectionClass = classOf[FoldTrueAndInspection]
}
