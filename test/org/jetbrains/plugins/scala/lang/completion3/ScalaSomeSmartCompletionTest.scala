package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaSomeSmartCompletionTest extends ScalaCompletionTestBase {
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
    val (activeLookup, _) = complete(2, CompletionType.SMART)

    val resultText =
"""
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val z = new A
  foo(Some(z)<caret>)
}
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "z").get)
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
    val (activeLookup, _) = complete(2, CompletionType.SMART)

    val resultText =
"""
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val z = new A
  foo(Some(z), <caret>)
}
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "z").get, ',')
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
    val (activeLookup, _) = complete(2, CompletionType.SMART)

    val resultText =
"""
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val z = new A
  val u: Option[A] = Some(z)<caret>
}
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "z").get)
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
    val (activeLookup, _) = complete(2, CompletionType.SMART)

    val resultText =
"""
class TUI {
  class A
  def foo(x: Option[A]) = 1
  val ko = new {def z: A = new A}
  val u: Option[A] = Some(ko.z)<caret>
}
""".replaceAll("\r", "").trim()

    if (activeLookup != null) {
      completeLookupItem(activeLookup.find(le => le.getLookupString == "z").get)
    }

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
    val (activeLookup, _) = complete(2, CompletionType.SMART)

    val resultText =
"""
class TUI {
  class A
  class B {def z(x: Int): A = new A}
  val ko = new B
  val u: Option[A] = Some(ko.z(<caret>))
}
""".replaceAll("\r", "").trim()

    if (activeLookup != null) {
      completeLookupItem(activeLookup.find(le => le.getLookupString == "z").get)
    }

    checkResultByText(resultText)
  }
}