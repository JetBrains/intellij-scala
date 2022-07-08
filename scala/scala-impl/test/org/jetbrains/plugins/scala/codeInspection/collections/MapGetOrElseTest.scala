package org.jetbrains.plugins.scala
package codeInspection
package collections

class MapGetOrElseTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[MapGetOrElseInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("map.getOrElse.hint")

  def test_1(): Unit = {
    val selected = s"None.${START}map(x => 1).getOrElse(0)$END"
    checkTextHasError(selected)

    val text = "None.map(x => 1).getOrElse(0)"
    val result = "None.fold(0)(x => 1)"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected = s"""class Test {
                     |  Some(0) ${START}map (_ => true) getOrElse false$END
                     |}""".stripMargin
    checkTextHasError(selected)

    val text = """class Test {
                 |  Some(0) map (_ => true) getOrElse false
                 |}""".stripMargin
    val result = """class Test {
                   |  Some(0).fold(false)(_ => true)
                   |}""".stripMargin
    testQuickFix(text, result, hint)
  }

  def test_3(): Unit = {
    val selected = s"""val function: (Any) => Boolean = _ => true
                      |(None ${START}map function).getOrElse(false)$END""".stripMargin
    checkTextHasError(selected)
    val text = """val function: (Any) => Int = _ => 0
                 |(None map function).getOrElse(1)""".stripMargin
    val result = """val function: (Any) => Int = _ => 0
                   |None.fold(1)(function)""".stripMargin
    testQuickFix(text, result, hint)
  }

  def test_4(): Unit = {
    val text = "None.map(x => Seq(0)).getOrElse(List(0))"
    checkTextHasNoErrors(text)
  }

  def test_5(): Unit = {
    val selected = s"None ${START}map {_ => 1} getOrElse {1}$END"
    checkTextHasError(selected)
    val text = "None map {_ => 1} getOrElse {1}"
    val result = "None.fold(1) { _ => 1 }"
    testQuickFix(text, result, hint)
  }

  def test_6(): Unit = {
    val selected = s"""Some(1) ${START}map (s => s + 1) getOrElse {
                     |  val x = 1
                     |  x
                     |}$END""".stripMargin
    checkTextHasError(selected)
    val text = """Some(1) map (s => s + 1) getOrElse {
                 |  val x = 1
                 |  x
                 |}""".stripMargin
    val result = """Some(1).fold {
                   |  val x = 1
                   |  x
                   |}(s => s + 1)""".stripMargin
    testQuickFix(text, result, hint)
  }

  def test_7(): Unit = {
    val text = "None.map(x => 0).getOrElse(1.1)"
    checkTextHasNoErrors(text)
  }

  def test_SCL7009(): Unit = {
    val text = "None.map(_ => Seq(1)).getOrElse(Seq.empty)"
    checkTextHasNoErrors(text)
  }
}
