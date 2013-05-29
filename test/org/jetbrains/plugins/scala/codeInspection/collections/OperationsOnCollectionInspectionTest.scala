package org.jetbrains.plugins.scala
package codeInspection.collections
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import com.intellij.codeInsight.CodeInsightTestCase
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 5/21/13
 */
abstract class OperationsOnCollectionInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val START = CodeInsightTestCase.SELECTION_START_MARKER
  val END = CodeInsightTestCase.SELECTION_END_MARKER
  val annotation = InspectionBundle.message("operation.on.collection.name")
  def hint: String

  protected def check(text: String) {
    checkTextHasError(text, annotation, classOf[OperationOnCollectionInspection])
  }

  protected def testFix(text: String, result: String, hint: String) {
    testQuickFix(text.replace("\r", ""), result.replace("\r", ""), hint, classOf[OperationOnCollectionInspection])
  }


}

class MapGetOrElseFalseTest extends OperationsOnCollectionInspectionTest {
  val hint: String = InspectionBundle.message("map.getOrElse.false.hint")

  def test_1() {
    val selected = s"None.${START}map(x => true).getOrElse(false)$END"
    check(selected)

    val text = "None.map(x => true).getOrElse(false)"
    val result = "None.exists(x => true)"
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
                   |  Some(0) exists (_ => true)
                   |}""".stripMargin
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"""  val valueIsGoodEnough: (Any) => Boolean = _ => true
                 |  (None ${START}map valueIsGoodEnough).getOrElse(false)$END""".stripMargin
    check(selected)
    val text = """  val valueIsGoodEnough: (Any) => Boolean = _ => true
                 |  (None map valueIsGoodEnough).getOrElse(false)""".stripMargin
    val result = """  val valueIsGoodEnough: (Any) => Boolean = _ => true
                   |  None exists valueIsGoodEnough""".stripMargin
    testFix(text, result, hint)
  }
}

class FindIsDefinedTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("find.isDefined.hint")
  def test_1() {
    val selected = s"""val valueIsGoodEnough: (Any) => Boolean = _ => true
                 |Nil.${START}find(valueIsGoodEnough).isDefined$END""".stripMargin
    check(selected)
    val text = """val valueIsGoodEnough: (Any) => Boolean = _ => true
                 |Nil.find(valueIsGoodEnough).isDefined""".stripMargin
    val result = """val valueIsGoodEnough: (Any) => Boolean = _ => true
                   |Nil.exists(valueIsGoodEnough)""".stripMargin
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"(Nil ${START}find (_ => true)) isDefined$END"
    check(selected)
    val text = "(Nil find (_ => true)) isDefined"
    val result = "Nil exists (_ => true)"
    testFix(text, result, hint)
  }
}

class FindNotEqualsNoneTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("find.notEquals.none.hint")
  def test_1() {
    val selected = s"(Nil ${START}find (_ => true)) != None$END"
    check(selected)
    val text = "(Nil find (_ => true)) != None"
    val result = "Nil exists (_ => true)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"Nil.${START}find(_ => true) != None$END"
    check(selected)
    val text = "Nil.find(_ => true) != None"
    val result = "Nil.exists(_ => true)"
    testFix(text, result, hint)
  }
}

class FilterHeadOptionTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("filter.headOption.hint")
  def test_1() {
    val selected = s"List(0).${START}filter(x => true).headOption$END"
    check(selected)
    val text = "List(0).filter(x => true).headOption"
    val result = "List(0).find(x => true)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"(List(0) ${START}filter (x => true)).headOption$END"
    check(selected)
    val text = "(List(0) filter (x => true)).headOption"
    val result = "List(0) find (x => true)"
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"List(0).${START}filter(x => true).headOption$END.isDefined"
    check(selected)
    val text = "List(0).filter(x => true).headOption.isDefined"
    val result = "List(0).find(x => true).isDefined"
    testFix(text, result, hint)
  }
}

class FilterSizeTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("filter.size.hint")
  def test_1() {
    val selected = s"None.${START}filter(x => true).size$END"
    check(selected)
    val text = "None.filter(x => true).size"
    val result = "None.count(x => true)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"None.${START}filter(x => true).length$END"
    check(selected)
    val text = "None.filter(x => true).length"
    val result = "None.count(x => true)"
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"Map() ${START}filter (x => true) size$END"
    check(selected)
    val text = "Map() filter (x => true) size"
    val result = "Map() count (x => true)"
    testFix(text, result, hint)
  }
}

class FoldLeftSumTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("foldLeft.sum.hint")
  def test_1() {
    val selected = s"List(0).$START/:(0)(_ + _)$END"
    check(selected)
    val text = "List(0)./:(0)(_ + _)"
    val result = "List(0).sum"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"Array(0).${START}foldLeft(0) ((_:Int) + _)$END"
    check(selected)
    val text = "Array(0).foldLeft(0) ((_:Int) + _)"
    val result = "Array(0).sum"
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"List(0).${START}foldLeft[Int](0) {(x,y) => x + y}$END"
    check(selected)
    val text = "List(0).foldLeft[Int](0) {(x,y) => x + y}"
    val result = "List(0).sum"
    testFix(text, result, hint)
  }

  //need to checkNumeric
//  def test_4() {
//    val text = s"""List("a").foldLeft(0)(_ + _)"""
//    checkTextHasNoErrors(text, annotation, classOf[OperationOnCollectionInspection])
//  }
}

class FoldLeftTrueAndTest extends OperationsOnCollectionInspectionTest {
  val hint = InspectionBundle.message("foldLeft.true.and.hint")
  def test_1() {
    val selected = s"List(false).${START}foldLeft(true){_ && _}$END"
    check(selected)
    val text = "List(false).foldLeft(true){_ && _}"
    val result = "List(false).forall(_)"
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"""def a(x: String) = false
                     |List("a").${START}foldLeft(true) (_ && a(_))$END""".stripMargin
    check(selected)
    val text = """def a(x: String) = false
                 |List("a").foldLeft(true) (_ && a(_))""".stripMargin
    val result = """def a(x: String) = false
                   |List("a").forall(a(_))""".stripMargin
    testFix(text, result, hint)
  }

  def test_3() {
    val selected = s"""def a(x: String) = false
                     |List("a").${START}foldLeft(true) ((x,y) => x && a(y))$END""".stripMargin
    check(selected)
    val text = """def a(x: String) = false
                 |List("a").foldLeft(true) ((x,y) => x && a(y))""".stripMargin
    val result = """def a(x: String) = false
                   |List("a").forall(y => a(y))""".stripMargin
    testFix(text, result, hint)
  }

  def test_4() {

    val text = """def a(x: String) = false
                 |List("a").foldLeft(true) ((x,y) => x && a(x))""".stripMargin
    checkTextHasNoErrors(text, annotation, classOf[OperationOnCollectionInspection])
  }
}

