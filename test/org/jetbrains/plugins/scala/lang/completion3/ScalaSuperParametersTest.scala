package org.jetbrains.plugins.scala
package lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase

/**
 * @author Alefas
 * @since 04.09.13
 */
class ScalaSuperParametersTest extends ScalaCodeInsightTestBase {
  def testConstructorCall() {
    val fileText =
      """
class TUI {
  class A(x: Int, y: Int) {
    def this(x: Int, y: Int, z: Int) = this(x, y)
  }

  class B(x: Int, y: Int, z: Int) extends A(<caret>)
}
      """.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.BASIC)

    val resultText =
      """
class TUI {
  class A(x: Int, y: Int) {
    def this(x: Int, y: Int, z: Int) = this(x, y)
  }

  class B(x: Int, y: Int, z: Int) extends A(x, y, z)<caret>
}
      """.replaceAll("\r", "").trim()

    finishLookup(lookups.find(le => le.getLookupString == "x, y, z").get)
    checkResultByText(resultText)
  }

  def testConstructorCall2() {
    val fileText =
      """
class TUI {
  class A(x: Int, y: Int) {
    def this(x: Int, y: Int, z: Int) = this(x, y)
  }

  class B(x: Int, y: Int, z: Int) extends A(<caret>)
}
      """.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.BASIC)

    val resultText =
      """
class TUI {
  class A(x: Int, y: Int) {
    def this(x: Int, y: Int, z: Int) = this(x, y)
  }

  class B(x: Int, y: Int, z: Int) extends A(x, y)<caret>
}
      """.replaceAll("\r", "").trim()

    finishLookup(lookups.find(le => le.getLookupString == "x, y").get)
    checkResultByText(resultText)
  }

  def testConstructorCall2Smart() {
    val fileText =
      """
class TUI {
  class A(x: Int, y: Int) {
    def this(x: Int, y: Int, z: Int) = this(x, y)
  }

  class B(x: Int, y: Int, z: Int) extends A(<caret>)
}
      """.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.SMART)

    val resultText =
      """
class TUI {
  class A(x: Int, y: Int) {
    def this(x: Int, y: Int, z: Int) = this(x, y)
  }

  class B(x: Int, y: Int, z: Int) extends A(x, y)<caret>
}
      """.replaceAll("\r", "").trim()

    finishLookup(lookups.find(le => le.getLookupString == "x, y").get)
    checkResultByText(resultText)
  }

  def testSuperCall() {
    val fileText =
      """
class TUI {
  class A {
    def foo(x: Int, y: Int, z: Int) = 1
    def foo(x: Int, y: Int) = 2
  }

  class B extends A {
    override def foo(x: Int, y: Int, z: Int) = {
      super.foo(<caret>)
    }
  }
}
      """.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.BASIC)

    val resultText =
      """
class TUI {
  class A {
    def foo(x: Int, y: Int, z: Int) = 1
    def foo(x: Int, y: Int) = 2
  }

  class B extends A {
    override def foo(x: Int, y: Int, z: Int) = {
      super.foo(x, y)<caret>
    }
  }
}
      """.replaceAll("\r", "").trim()

    finishLookup(lookups.find(le => le.getLookupString == "x, y").get)
    checkResultByText(resultText)
  }

  def testSuperCall2() {
    val fileText =
      """
class TUI {
  class A {
    def foo(x: Int, y: Int, z: Int) = 1
    def foo(x: Int, y: Int) = 2
  }

  class B extends A {
    override def foo(x: Int, y: Int, z: Int) = {
      super.foo(<caret>)
    }
  }
}
      """.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.BASIC)

    val resultText =
      """
class TUI {
  class A {
    def foo(x: Int, y: Int, z: Int) = 1
    def foo(x: Int, y: Int) = 2
  }

  class B extends A {
    override def foo(x: Int, y: Int, z: Int) = {
      super.foo(x, y, z)<caret>
    }
  }
}
      """.replaceAll("\r", "").trim()

    finishLookup(lookups.find(le => le.getLookupString == "x, y, z").get)
    checkResultByText(resultText)
  }

}
