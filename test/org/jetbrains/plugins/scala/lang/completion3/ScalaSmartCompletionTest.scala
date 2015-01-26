package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.junit.Assert

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.10.11
 */

class ScalaSmartCompletionTest extends ScalaCodeInsightTestBase {
  def testAfterPlaceholder() {
    val fileText =
      """
      |class A {
      |  class B {def concat: B = new B}
      |  val f: B => B = _.<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A {
      |  class B {def concat: B = new B}
      |  val f: B => B = _.concat<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "concat").get)
    checkResultByText(resultText)
  }

  def testTimeUnit1() {
    val fileText =
      """
        |class TimeUnit
        |object TimeUnit {
        |  val HOURS = new TimeUnit
        |  val DAYS = new TimeUnit
        |}
        |
        |def foo() = {
        |  bar(TimeUnit.<caret>HOURS)
        |}
        |
        |def bar(unit: TimeUnit) {}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
        |class TimeUnit
        |object TimeUnit {
        |  val HOURS = new TimeUnit
        |  val DAYS = new TimeUnit
        |}
        |
        |def foo() = {
        |  bar(TimeUnit.DAYS<caret>)
        |}
        |
        |def bar(unit: TimeUnit) {}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("DAYS")).get)
    checkResultByText(resultText)
  }

  def testTimeUnit2() {
    val fileText =
      """
        |class TimeUnit
        |object TimeUnit {
        |  val HOURS = new TimeUnit
        |  val DAYS = new TimeUnit
        |}
        |
        |def foo() = {
        |  bar(Time<caret>Unit.HOURS)
        |}
        |
        |def bar(unit: TimeUnit) {}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
        |class TimeUnit
        |object TimeUnit {
        |  val HOURS = new TimeUnit
        |  val DAYS = new TimeUnit
        |}
        |
        |def foo() = {
        |  bar(TimeUnit.DAYS<caret>)
        |}
        |
        |def bar(unit: TimeUnit) {}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("DAYS")).get)
    checkResultByText(resultText)
  }

  def testAfterNew() {
    val fileText =
      """
      |import scala.collection.mutable.HashSet
      |class A {
      |  val f: HashSet[String] = new <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |import scala.collection.mutable
      |import scala.collection.mutable.HashSet
      |class A {
      |  val f: HashSet[String] = new mutable.HashSet[String]()
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "HashSet").get, '[')
    checkResultByText(resultText)
  }
  
  def testFilterPrivates() {
    val fileText =
      """
      |class Test {
      |  def foo(): String = ""
      |  private def bar(): String = ""
      |}
      |
      |object O extends App {
      |  val s: String = new Test().bar<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    Assert.assertNull(activeLookup)
  }

  def testFilterObjectDouble() {
    val fileText =
      """
      |class Test {
      |  val x: Double = <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    Assert.assertTrue(activeLookup.find(_.getLookupString == "Double") == None)
  }

  def testFalse() {
    val fileText =
      """
      |class A {
      |  val f: Boolean = <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A {
      |  val f: Boolean = false<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "false").get, '\t')
    checkResultByText(resultText)
  }

  def testClassOf() {
    val fileText =
      """
      |class A {
      |  val f: Class[_] = <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A {
      |  val f: Class[_] = classOf[<caret>]
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "classOf").get, '\t')
    checkResultByText(resultText)
  }

  def testSmartRenamed() {
    val fileText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BL<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BLLLL[Int](<caret>)
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "BLLLL").get, '\t')
    checkResultByText(resultText)
  }

  def testThis() {
    val fileText =
      """
      |class TT {
      |  val al: TT = <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class TT {
      |  val al: TT = this<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "this").get, '\t')
    checkResultByText(resultText)
  }

  def testInnerThis() {
    val fileText =
      """
      |class TT {
      |  class GG {
      |    val al: GG = <caret>
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class TT {
      |  class GG {
      |    val al: GG = this<caret>
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "this").get, '\t')
    checkResultByText(resultText)
  }

  def testOuterThis() {
    val fileText =
      """
      |class TT {
      |  class GG {
      |    val al: TT = <caret>
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class TT {
      |  class GG {
      |    val al: TT = TT.this<caret>
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "TT.this").get, '\t')
    checkResultByText(resultText)
  }

  def testWhile() {
    val fileText =
      """
      |while (<caret>) {}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |while (true<caret>) {}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "true").get, '\t')
    checkResultByText(resultText)
  }

  def testDoWhile() {
    val fileText =
      """
      |do {} while (<caret>)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |do {} while (true<caret>)
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "true").get, '\t')
    checkResultByText(resultText)
  }

  def testNewFunction() {
    val fileText =
      """
      |val x: Int => String = new <caret>
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |val x: Int => String = new Function[Int, String] {
      |  def apply(v1: Int): String = <selection>???</selection>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "Function1").get, '\t')
    checkResultByText(resultText)
  }

  def testEtaExpansion() {
    val fileText =
      """
      |def foo(x: Int): String = x.toString
      |val x: Int => String = <caret>
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |def foo(x: Int): String = x.toString
      |val x: Int => String = foo _<caret>
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "foo").get, '\t')
    checkResultByText(resultText)
  }

  def testJavaEnum() {
    val javaFileText =
      """
      |package a;
      |
      |public enum Java {
      |  aaa, bbb, ccc
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val fileText =
      """
      |import a.Java
      |class A {
      |  val x: Java = a<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val myVFile = getSourceRootAdapter.createChildDirectory(null, "a").createChildData(null, "Java.java")
    VfsUtil.saveText(myVFile, javaFileText)
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |import a.Java
      |class A {
      |  val x: Java = Java.aaa<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "aaa").get, '\t')
    checkResultByText(resultText)
  }

  def testScalaEnum() {
    val fileText =
      """
      |object Scala extends Enumeration {type Scala = Value; val aaa, bbb, ccc = Value}
      |class A {
      |  val x: Scala.Scala = a<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |object Scala extends Enumeration {type Scala = Value; val aaa, bbb, ccc = Value}
      |class A {
      |  val x: Scala.Scala = Scala.aaa<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "aaa").get, '\t')
    checkResultByText(resultText)
  }

  def testScalaFactoryMethod() {
    val fileText =
      """
      |class Scala
      |object Scala {
      |  def getInstance() = new Scala
      |}
      |class A {
      |  val x: Scala = get<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class Scala
      |object Scala {
      |  def getInstance() = new Scala
      |}
      |class A {
      |  val x: Scala = Scala.getInstance()<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "getInstance").get, '\t')
    checkResultByText(resultText)
  }

  def testScalaFactoryApply() {
    val fileText =
      """
      |case class Scala()
      |class A {
      |  val x: Scala = <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |case class Scala()
      |class A {
      |  val x: Scala = Scala.apply()<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "apply").get, '\t')
    checkResultByText(resultText)
  }

  def testScalaHashSetEmpty() {
    val fileText =
      """
      |import collection.mutable.HashSet
      |class A {
      |  val x: HashSet[String] = <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |import collection.mutable.HashSet
      |class A {
      |  val x: HashSet[String] = HashSet.empty<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "empty").get, '\t')
    checkResultByText(resultText)
  }

  def testTwoGenerics() {
    val fileText =
      """
        |class A[T, K](s: Int)
        |
        |class B[T, K](s: Int) extends A[T, K](s)
        |
        |val map: A[Int,Int] = new <caret>
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A[T, K](s: Int)
      |
      |class B[T, K](s: Int) extends A[T, K](s)
      |
      |val map: A[Int,Int] = new B[Int, Int](<caret>)
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "B").get, '\t')
    checkResultByText(resultText)
  }
  
  def testChainedSecondCompletion() {
    val fileText =
      """
      |object YY {
      |  def foo(): YY = new YY
      |  val x: OP = <caret>
      |}
      |class YY {
      |  def goo(x: Int) = new OP
      |}
      |class OP
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(2, CompletionType.SMART)

    val resultText =
      """
      |object YY {
      |  def foo(): YY = new YY
      |  val x: OP = foo().goo(<caret>)
      |}
      |class YY {
      |  def goo(x: Int) = new OP
      |}
      |class OP
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "foo.goo").get, '\t')
    checkResultByText(resultText)
  }
}