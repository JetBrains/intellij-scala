package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.CompletionType.SMART
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings.{alwaysAddType, set}

/**
  * User: Alexander Podkhalyuzin
  * Date: 28.10.11
  */
class ScalaSmartCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET, SELECTION_END_TAG => E, SELECTION_START_TAG => S}

  def testAfterPlaceholder(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  class B {def concat: B = new B}
         |  val f: B => B = _.$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  class B {def concat: B = new B}
         |  val f: B => B = _.concat$CARET
         |}
      """.stripMargin,
    item = "concat",
    completionType = SMART
  )

  def testTimeUnit1(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TimeUnit
         |object TimeUnit {
         |  val HOURS = new TimeUnit
         |  val DAYS = new TimeUnit
         |}
         |
         |def foo() = {
         |  bar(TimeUnit.${CARET}HOURS)
         |}
         |
         |def bar(unit: TimeUnit) {}
      """.stripMargin,
    resultText =
      s"""
         |class TimeUnit
         |object TimeUnit {
         |  val HOURS = new TimeUnit
         |  val DAYS = new TimeUnit
         |}
         |
         |def foo() = {
         |  bar(TimeUnit.DAYS$CARET)
         |}
         |
         |def bar(unit: TimeUnit) {}
      """.stripMargin,
    item = "DAYS",
    completionType = SMART
  )

  def testTimeUnit2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TimeUnit
         |object TimeUnit {
         |  val HOURS = new TimeUnit
         |  val DAYS = new TimeUnit
         |}
         |
         |def foo() = {
         |  bar(Time${CARET}Unit.HOURS)
         |}
         |
         |def bar(unit: TimeUnit) {}
        """.stripMargin,
    resultText =
      s"""
         |class TimeUnit
         |object TimeUnit {
         |  val HOURS = new TimeUnit
         |  val DAYS = new TimeUnit
         |}
         |
         |def foo() = {
         |  bar(TimeUnit.DAYS$CARET)
         |}
         |
         |def bar(unit: TimeUnit) {}
        """.stripMargin,
    item = "DAYS",
    completionType = SMART
  )

  def testAfterNew(): Unit = doCompletionTest(
    fileText =
      s"""
         |import scala.collection.mutable.HashSet
         |
         |class A {
         |  val f: HashSet[String] = new $CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import scala.collection.mutable
         |import scala.collection.mutable.HashSet
         |
         |class A {
         |  val f: HashSet[String] = new mutable.HashSet[String]($CARET)
         |}
      """.stripMargin,
    item = "HashSet",
    char = '[',
    completionType = SMART
  )

  def testAfterNewNoObject(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class testAfterNewNoObject {
         |  val atest: Atest = new $CARET
         |}
         |
         |class Atest
         |
         |object OTest extends Atest
      """.stripMargin,
    item = "OTest",
    completionType = SMART
  )

  def testFilterPrivates(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class Test {
         |  def foo(): String = ""
         |  private def bar(): String = ""
         |}
         |
         |object O extends App {
         |  val s: String = new Test().bar$CARET
         |}
      """.stripMargin,
    time = 1,
    completionType = SMART
  ) {
    _ => true
  }

  def testFilterObjectDouble(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class Test {
         |  val x: Double = $CARET
         |}
      """.stripMargin,
    item = "Double",
    completionType = SMART
  )

  def testFalse(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  val f: Boolean = $CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  val f: Boolean = false$CARET
         |}
      """.stripMargin,
    item = "false",
    completionType = SMART
  )

  def testClassOf(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  val f: Class[_] = $CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  val f: Class[_] = classOf[$CARET]
         |}
      """.stripMargin,
    item = "classOf",
    completionType = SMART
  )

  def testSmartRenamed(): Unit = doCompletionTest(
    fileText =
      s"""
         |import java.util.{ArrayList => BLLLL}
         |object Test extends App {
         |  val al: java.util.List[Int] = new BL$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import java.util.{ArrayList => BLLLL}
         |object Test extends App {
         |  val al: java.util.List[Int] = new BLLLL[Int]($CARET)
         |}
      """.stripMargin,
    item = "BLLLL",
    completionType = SMART
  )

  def testThis(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TT {
         |  val al: TT = $CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class TT {
         |  val al: TT = this$CARET
         |}
      """.stripMargin,
    item = "this",
    completionType = SMART
  )

  def testInnerThis(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TT {
         |  class GG {
         |    val al: GG = $CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class TT {
         |  class GG {
         |    val al: GG = this$CARET
         |  }
         |}
      """.stripMargin,
    item = "this",
    completionType = SMART
  )

  def testOuterThis(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TT {
         |  class GG {
         |    val al: TT = $CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class TT {
         |  class GG {
         |    val al: TT = TT.this$CARET
         |  }
         |}
      """.stripMargin,
    item = "TT.this",
    completionType = SMART
  )

  def testWhile(): Unit = doCompletionTest(
    fileText =
      s"""
         |while ($CARET) {}
      """.stripMargin,
    resultText =
      s"""
         |while (true$CARET) {}
      """.stripMargin,
    item = "true",
    completionType = SMART
  )

  def testDoWhile(): Unit = doCompletionTest(
    fileText =
      s"""
         |do {} while ($CARET)
      """.stripMargin,
    resultText =
      s"""
         |do {} while (true$CARET)
      """.stripMargin,
    item = "true",
    completionType = SMART
  )

  //Return type for inserting method is generated according to TypeAnnotations Settings
  def testNewFunction(): Unit = {
    val project = getProject
    set(project, alwaysAddType(ScalaCodeStyleSettings.getInstance(project)))

    doCompletionTest(
      fileText =
        s"""
           |val x: Int => String = new $CARET
        """.stripMargin,
      resultText =
        s"""
           |val x: Int => String = new Function[Int, String] {
           |  def apply(v1: Int): String = $S???$E
           |}
        """.stripMargin,
      item = "Function1",
      completionType = SMART
    )
  }

  def testEtaExpansion(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Int): String = x.toString
         |val x: Int => String = $CARET
      """.stripMargin,
    resultText =
      s"""
         |def foo(x: Int): String = x.toString
         |val x: Int => String = foo _$CARET
      """.stripMargin,
    item = "foo",
    completionType = SMART
  )

  def testJavaEnum(): Unit = {
    configureJavaFile(
      fileText =
        """
          |package a;
          |
          |public enum Java {
          |  aaa, bbb, ccc
          |}
        """.stripMargin,
      className = "Java",
      packageName = "a"
    )

    doCompletionTest(
      fileText =
        s"""
           |import a.Java
           |class A {
           |  val x: Java = a$CARET
           |}
        """.stripMargin,
      resultText =
        s"""
           |import a.Java
           |class A {
           |  val x: Java = Java.aaa$CARET
           |}
        """.stripMargin,
      item = "aaa",
      completionType = SMART
    )
  }

  def testScalaEnum(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Scala extends Enumeration {type Scala = Value; val aaa, bbb, ccc = Value}
         |class A {
         |  val x: Scala.Scala = a$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Scala extends Enumeration {type Scala = Value; val aaa, bbb, ccc = Value}
         |class A {
         |  val x: Scala.Scala = Scala.aaa$CARET
         |}
      """.stripMargin,
    item = "aaa",
    completionType = SMART
  )

  def testScalaFactoryMethod(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Scala
         |object Scala {
         |  def getInstance() = new Scala
         |}
         |class A {
         |  val x: Scala = get$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class Scala
         |object Scala {
         |  def getInstance() = new Scala
         |}
         |class A {
         |  val x: Scala = Scala.getInstance()$CARET
         |}
      """.stripMargin,
    item = "getInstance",
    completionType = SMART
  )

  def testScalaFactoryApply(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Scala()
         |class A {
         |  val x: Scala = $CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |case class Scala()
         |class A {
         |  val x: Scala = Scala.apply()$CARET
         |}
      """.stripMargin,
    item = "apply",
    completionType = SMART
  )

  def testScalaHashSetEmpty(): Unit = doCompletionTest(
    fileText =
      s"""
         |import collection.mutable.HashSet
         |class A {
         |  val x: HashSet[String] = $CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import collection.mutable.HashSet
         |class A {
         |  val x: HashSet[String] = HashSet.empty$CARET
         |}
      """.stripMargin,
    item = "empty",
    completionType = SMART
  )

  def testTwoGenerics(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A[T, K](s: Int)
         |
         |class B[T, K](s: Int) extends A[T, K](s)
         |
         |val map: A[Int,Int] = new $CARET
      """.stripMargin,
    resultText =
      s"""
         |class A[T, K](s: Int)
         |
         |class B[T, K](s: Int) extends A[T, K](s)
         |
         |val map: A[Int,Int] = new B[Int, Int]($CARET)
      """.stripMargin,
    item = "B",
    completionType = SMART
  )

  def testChainedSecondCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |object YY {
         |  def foo(): YY = new YY
         |  val x: OP = $CARET
         |}
         |class YY {
         |  def goo(x: Int) = new OP
         |}
         |class OP
      """.stripMargin,
    resultText =
      s"""
         |object YY {
         |  def foo(): YY = new YY
         |  val x: OP = foo().goo($CARET)
         |}
         |class YY {
         |  def goo(x: Int) = new OP
         |}
         |class OP
      """.stripMargin,
    item = "foo.goo",
    time = 2,
    completionType = SMART
  )
}