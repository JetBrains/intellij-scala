package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
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
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A {
      |  class B {def concat: B = new B}
      |  val f: B => B = _.concat<caret>
      |}
      """

    completeLookupItem(activeLookup.find(le => le.getLookupString == "concat").get)
    checkResultByText(normalize(resultText))
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
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
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
      """

    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("DAYS")).get)
    checkResultByText(normalize(resultText))
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
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
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
      """

    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("DAYS")).get)
    checkResultByText(normalize(resultText))
  }

  def testAfterNew() {
    val fileText =
      """
      |import scala.collection.mutable.HashSet
      |
      |class A {
      |  val f: HashSet[String] = new <caret>
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |import scala.collection.mutable
      |import scala.collection.mutable.HashSet
      |
      |class A {
      |  val f: HashSet[String] = new mutable.HashSet[String]()
      |}
      """

    completeLookupItem(activeLookup.find(le => le.getLookupString == "HashSet").get, '[')
    checkResultByText(normalize(resultText))
  }

  def testAfterNewNoObject(): Unit ={
    val fileText =
      """
        |class testAfterNewNoObject {
        |  val atest: Atest = new <caret>
        |}
        |
        |class Atest
        |
        |object OTest extends Atest
      """

    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    Assert.assertTrue(
      "Smart Completion shouldn't contain Objects in after new position",
      !activeLookup.exists(_.getLookupString == "OTest"))
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
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    Assert.assertNull(activeLookup)
  }

  def testFilterObjectDouble() {
    val fileText =
      """
      |class Test {
      |  val x: Double = <caret>
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    Assert.assertTrue(!activeLookup.exists(_.getLookupString == "Double"))
  }

  def testFalse() {
    val fileText =
      """
      |class A {
      |  val f: Boolean = <caret>
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A {
      |  val f: Boolean = false<caret>
      |}
      """

    completeLookupItem(activeLookup.find(le => le.getLookupString == "false").get)
    checkResultByText(normalize(resultText))
  }

  def testClassOf() {
    val fileText =
      """
      |class A {
      |  val f: Class[_] = <caret>
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A {
      |  val f: Class[_] = classOf[<caret>]
      |}
      """

    completeLookupItem(activeLookup.find(le => le.getLookupString == "classOf").get)
    checkResultByText(normalize(resultText))
  }

  def testSmartRenamed() {
    val fileText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BL<caret>
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BLLLL[Int](<caret>)
      |}
      """

    completeLookupItem(activeLookup.find(le => le.getLookupString == "BLLLL").get)
    checkResultByText(normalize(resultText))
  }

  def testThis() {
    val fileText =
      """
      |class TT {
      |  val al: TT = <caret>
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class TT {
      |  val al: TT = this<caret>
      |}
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "this").get)
    checkResultByText(normalize(resultText))
  }

  def testInnerThis() {
    val fileText =
      """
      |class TT {
      |  class GG {
      |    val al: GG = <caret>
      |  }
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class TT {
      |  class GG {
      |    val al: GG = this<caret>
      |  }
      |}
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "this").get)
    checkResultByText(normalize(resultText))
  }

  def testOuterThis() {
    val fileText =
      """
      |class TT {
      |  class GG {
      |    val al: TT = <caret>
      |  }
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class TT {
      |  class GG {
      |    val al: TT = TT.this<caret>
      |  }
      |}
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "TT.this").get)
    checkResultByText(normalize(resultText))
  }

  def testWhile() {
    val fileText =
      """
      |while (<caret>) {}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |while (true<caret>) {}
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "true").get)
    checkResultByText(normalize(resultText))
  }

  def testDoWhile() {
    val fileText =
      """
      |do {} while (<caret>)
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |do {} while (true<caret>)
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "true").get)
    checkResultByText(normalize(resultText))
  }

  //Return type for inserting method is generated according to TypeAnnotations Settings
  def testNewFunction() {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val fileText =
      """
      |val x: Int => String = new <caret>
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |val x: Int => String = new Function[Int, String] {
      |  def apply(v1: Int): String = <selection>???</selection>
      |}
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "Function1").get)
    checkResultByText(normalize(resultText))
  }

  def testEtaExpansion() {
    val fileText =
      """
      |def foo(x: Int): String = x.toString
      |val x: Int => String = <caret>
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |def foo(x: Int): String = x.toString
      |val x: Int => String = foo _<caret>
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "foo").get)
    checkResultByText(normalize(resultText))
  }

  def testJavaEnum() {
    val javaFileText =
      """
      |package a;
      |
      |public enum Java {
      |  aaa, bbb, ccc
      |}
      """
    val fileText =
      """
      |import a.Java
      |class A {
      |  val x: Java = a<caret>
      |}
      """

    inWriteAction {
      val myVFile = getSourceRootAdapter.createChildDirectory(null, "a").createChildData(null, "Java.java")
      VfsUtil.saveText(myVFile, normalize(javaFileText))
    }

    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |import a.Java
      |class A {
      |  val x: Java = Java.aaa<caret>
      |}
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "aaa").get)
    checkResultByText(normalize(resultText))
  }

  def testScalaEnum() {
    val fileText =
      """
      |object Scala extends Enumeration {type Scala = Value; val aaa, bbb, ccc = Value}
      |class A {
      |  val x: Scala.Scala = a<caret>
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |object Scala extends Enumeration {type Scala = Value; val aaa, bbb, ccc = Value}
      |class A {
      |  val x: Scala.Scala = Scala.aaa<caret>
      |}
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "aaa").get)
    checkResultByText(normalize(resultText))
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
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
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
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "getInstance").get)
    checkResultByText(normalize(resultText))
  }

  def testScalaFactoryApply() {
    val fileText =
      """
      |case class Scala()
      |class A {
      |  val x: Scala = <caret>
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |case class Scala()
      |class A {
      |  val x: Scala = Scala.apply()<caret>
      |}
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "apply").get)
    checkResultByText(normalize(resultText))
  }

  def testScalaHashSetEmpty() {
    val fileText =
      """
      |import collection.mutable.HashSet
      |class A {
      |  val x: HashSet[String] = <caret>
      |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |import collection.mutable.HashSet
      |class A {
      |  val x: HashSet[String] = HashSet.empty<caret>
      |}
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "empty").get)
    checkResultByText(normalize(resultText))
  }

  def testTwoGenerics() {
    val fileText =
      """
        |class A[T, K](s: Int)
        |
        |class B[T, K](s: Int) extends A[T, K](s)
        |
        |val map: A[Int,Int] = new <caret>
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A[T, K](s: Int)
      |
      |class B[T, K](s: Int) extends A[T, K](s)
      |
      |val map: A[Int,Int] = new B[Int, Int](<caret>)
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "B").get)
    checkResultByText(normalize(resultText))
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
      """
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
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
      """

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "foo.goo").get)
    checkResultByText(normalize(resultText))
  }
}