package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.testFramework.EditorTestUtil

/**
  * @author Pavel Fatin
  */
class ScalaAotCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testParameterName(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Dummy {
         |  class Foo
         |  def f(f$CARET)
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Dummy {
         |  class Foo
         |  def f(foo: Foo$CARET)
         |}
      """.stripMargin,
    item = "Foo",
    presentationText = "foo: Foo"
  )

  def testValueName(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Dummy {
         |  class Foo
         |  val f$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Dummy {
         |  class Foo
         |  val foo$CARET
         |}
      """.stripMargin,
    item = "Foo",
    presentationText = "foo"
  )

  def testVariableName(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Foo
         |var f$CARET
      """.stripMargin,
    resultText =
      s"""
         |class Foo
         |var foo$CARET
      """.stripMargin,
    item = "Foo",
    presentationText = "foo"
  )

  def testPartialName(): Unit = doCompletionTest(
    fileText =
      s"""
         |class FooBarMoo
         |def f(ba$CARET)
      """.stripMargin,
    resultText =
      s"""
         |class FooBarMoo
         |def f(barMoo: FooBarMoo$CARET)
      """.stripMargin,
    item = "FooBarMoo",
    presentationText = "barMoo: FooBarMoo"
  )

  def testImport(): Unit = doCompletionTest(
    fileText =
      s"""
         |def f(rectangle$CARET)
      """.stripMargin,
    resultText =
      s"""
         |import java.awt.Rectangle
         |
         |def f(rectangle: Rectangle$CARET)
      """.stripMargin,
    item = "Rectangle",
    presentationText = "rectangle: Rectangle"
  )

  import ScalaCodeInsightTestBase._

  private def doCompletionTest(fileText: String,
                               resultText: String,
                               item: String,
                               presentationText: String): Unit =
    doCompletionTest(
      fileText = fileText,
      resultText = resultText,
      char = DEFAULT_CHAR,
      time = DEFAULT_TIME,
      completionType = DEFAULT_COMPLETION_TYPE
    ) { lookup =>
      hasLookupString(lookup, item) &&
        renderLookupElement(lookup).getItemText == presentationText
    }
}
