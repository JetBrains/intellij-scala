package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase

/**
  * @author Alexander Podkhalyuzin
  */

class ScalaSomeSmartCompletionTest extends ScalaCodeInsightTestBase {
  def testSomeSmart1() {
    val fileText =
      """
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val z = new A
  foo(<caret>)
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(2, CompletionType.SMART)

    val resultText =
      """
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val z = new A
  foo(Some(z)<caret>)
}
""".replaceAll("\r", "").trim()

    finishLookup(lookups.find(le => le.getLookupString == "z").get)
    checkResultByText(resultText)
  }

  def testSomeSmart2() {
    val fileText =
      """
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val z = new A
  foo(<caret>)
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(2, CompletionType.SMART)

    val resultText =
      """
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val z = new A
  foo(Some(z),<caret>)
}
""".replaceAll("\r", "").trim()

    finishLookup(lookups.find(le => le.getLookupString == "z").get, ',')
    checkResultByText(resultText)
  }

  def testSomeSmart3() {
    val fileText =
      """
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val z = new A
  val u: Option[A] = <caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(2, CompletionType.SMART)

    val resultText =
      """
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val z = new A
  val u: Option[A] = Some(z)<caret>
}
""".replaceAll("\r", "").trim()

    finishLookup(lookups.find(le => le.getLookupString == "z").get)
    checkResultByText(resultText)
  }

  def testSomeSmart4() {
    val fileText =
      """
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val ko = new {def z: A = new A}
  val u: Option[A] = ko.<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(2, CompletionType.SMART)

    val resultText =
      """
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val ko = new {def z: A = new A}
  val u: Option[A] = Some(ko.z)<caret>
}
""".replaceAll("\r", "").trim()

    lookups.find(le => le.getLookupString == "z")
      .foreach(finishLookup(_))

    checkResultByText(resultText)
  }

  def testSomeSmart5() {
    val fileText =
      """
class TUI {
  class A
  class B {def z(x: Int): A = new A}
  val ko = new B
  val u: Option[A] = ko.<caret>
}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(2, CompletionType.SMART)

    val resultText =
      """
class TUI {
  class A
  class B {def z(x: Int): A = new A}
  val ko = new B
  val u: Option[A] = Some(ko.z(<caret>))
}
""".replaceAll("\r", "").trim()

    lookups.find(le => le.getLookupString == "z")
      .foreach(finishLookup(_))

    checkResultByText(resultText)
  }

  def testOuterThis() {
    val fileText =
      """
        |class TT {
        |  class GG {
        |    val al: Option[TT] = <caret>
        |  }
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(2, CompletionType.SMART)

    val resultText =
      """
        |class TT {
        |  class GG {
        |    val al: Option[TT] = Some(TT.this)<caret>
        |  }
        |}
      """.stripMargin.replaceAll("\r", "").trim()

    lookups.find(le => le.getLookupString == "TT.this")
      .foreach(finishLookup(_))

    checkResultByText(resultText)
  }

  def testSomeScalaEnum() {
    val fileText =
      """
        |object Scala extends Enumeration {type Scala = Value; val aaa, bbb, ccc = Value}
        |class A {
        |  val x: Option[Scala.Scala] = a<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(2, CompletionType.SMART)

    val resultText =
      """
        |object Scala extends Enumeration {type Scala = Value; val aaa, bbb, ccc = Value}
        |class A {
        |  val x: Option[Scala.Scala] = Some(Scala.aaa)<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()

    lookups.find(le => le.getLookupString == "aaa")
      .foreach(finishLookup(_))

    checkResultByText(resultText)
  }
}