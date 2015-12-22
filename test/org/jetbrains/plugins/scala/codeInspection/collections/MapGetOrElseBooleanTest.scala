package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/30/13
 */
class MapGetOrElseBooleanTest extends OperationsOnCollectionInspectionTest {
  val hint: String = InspectionBundle.message("map.getOrElse.false.hint")
  val hintTrue: String = InspectionBundle.message("map.getOrElse.true.hint")

  def test_1() {
    val selected = s"None.${START}map(x => true).getOrElse(false)$END"
    check(selected)

    val text = "None.map(x => true).getOrElse(false)"
    val result = "None.exists(x => true)"
    testFix(text, result, hint)
  }

  "aaa ".split(' ')

  def test_2() {
    val selected =
      s"""class Test {
          |  Some(0) ${START}map (_ => true) getOrElse false$END
          |}""".stripMargin
    check(selected)

    val text =
      """class Test {
        |  Some(0) map (_ => true) getOrElse false
        |}""".stripMargin
    val result =
      """class Test {
        |  Some(0) exists (_ => true)
        |}""".stripMargin
    testFix(text, result, hint)
  }

  def test_3() {
    val selected =
      s"""val valueIsGoodEnough: (Any) => Boolean = _ => true
          |(None ${START}map valueIsGoodEnough).getOrElse(false)$END""".stripMargin
    check(selected)
    val text =
      """val valueIsGoodEnough: (Any) => Boolean = _ => true
        |(None map valueIsGoodEnough).getOrElse(false)""".stripMargin
    val result =
      """val valueIsGoodEnough: (Any) => Boolean = _ => true
        |None exists valueIsGoodEnough""".stripMargin
    testFix(text, result, hint)
  }

  def test_4() {
    val selected =
      s"""val valueIsGoodEnough: (Any) => Boolean = _ => true
          |(None ${START}map valueIsGoodEnough).getOrElse(true)$END""".stripMargin
    check(selected, hintTrue)
    val text =
      """val valueIsGoodEnough: (Any) => Boolean = _ => true
        |(None map valueIsGoodEnough).getOrElse(true)""".stripMargin
    val result =
      """val valueIsGoodEnough: (Any) => Boolean = _ => true
        |None forall valueIsGoodEnough""".stripMargin
    testFix(text, result, hintTrue)
  }

  override val inspectionClass = classOf[MapGetOrElseBooleanInspection]
}
