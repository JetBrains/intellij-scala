package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class MapGetOrElseTest extends OperationsOnCollectionInspectionTest {
  val hint: String = InspectionBundle.message("map.getOrElse.hint")

  override val inspectionClass = classOf[MapGetOrElseInspection]

  def test_1() {
    val selected = s"None.${START}map(x => 1).getOrElse(0)$END"
    check(selected)

    val text = "None.map(x => 1).getOrElse(0)"
    val result = "None.fold(0)(x => 1)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"""class Test {
                     |  Some(0) ${START}map (_ => true) getOrElse false$END
                     |}""".stripMargin
    check(selected)

    val text = """class Test {
                 |  Some(0) map (_ => true) getOrElse false
                 |}""".stripMargin
    val result = """class Test {
                   |  Some(0).fold(false)(_ => true)
                   |}""".stripMargin
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"""val function: (Any) => Boolean = _ => true
                      |(None ${START}map function).getOrElse(false)$END""".stripMargin
    check(selected)
    val text = """val function: (Any) => Int = _ => 0
                 |(None map function).getOrElse(1)""".stripMargin
    val result = """val function: (Any) => Int = _ => 0
                   |None.fold(1)(function)""".stripMargin
    testFix(text, result, hint)
  }

  def test_4() {
    val text = "None.map(x => Seq(0)).getOrElse(List(0))"
    val text2 = "None.map(x => 0).getOrElse(1.1)"
    Seq(text, text2).foreach { t =>
      checkTextHasNoErrors(t, hint, inspectionClass)
    }
  }

  def test_5() {
    val selected = s"None ${START}map {_ => 1} getOrElse {1}$END"
    check(selected)
    val text = "None map {_ => 1} getOrElse {1}"
    val result = "None.fold(1) { _ => 1 }"
    testFix(text, result, hint)
  }

  def test_6() {
    val selected = s"""Some(1) ${START}map (s => s + 1) getOrElse {
                     |  val x = 1
                     |  x
                     |}$END""".stripMargin
    check(selected)
    val text = """Some(1) map (s => s + 1) getOrElse {
                 |  val x = 1
                 |  x
                 |}""".stripMargin
    val result = """Some(1).fold {
                   |  val x = 1
                   |  x
                   |}(s => s + 1)""".stripMargin
    testFix(text, result, hint)
  }

  def test_SCL7009() {
    val text = "None.map(_ => Seq(1)).getOrElse(Seq.empty)"
    checkTextHasNoErrors(text, hint, inspectionClass)
  }
}
