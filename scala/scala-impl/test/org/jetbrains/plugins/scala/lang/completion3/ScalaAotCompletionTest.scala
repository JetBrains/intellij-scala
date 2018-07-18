package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl

/**
  * @author Pavel Fatin
  */
class ScalaAotCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}
  import ScalaCodeInsightTestBase._

  def testParameterName(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |def foo(f$CARET)
      """.stripMargin,
    resultText =
      s"""class Foo
         |def foo(foo: Foo$CARET)
      """.stripMargin,
    lookupString = "Foo",
    itemText = "foo: Foo"
  )

  def testValueName(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |val f$CARET
      """.stripMargin,
    resultText =
      s"""class Foo
         |val foo$CARET
      """.stripMargin,
    lookupString = "Foo",
    itemText = "foo",
    tailTextSuffix = null
  )

  def testVariableName(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |var f$CARET
      """.stripMargin,
    resultText =
      s"""class Foo
         |var foo$CARET
      """.stripMargin,
    lookupString = "Foo",
    itemText = "foo",
    tailTextSuffix = null
  )

  def testPartialName(): Unit = doAotCompletionTest(
    fileText =
      s"""class FooBarBaz
         |def foo(ba$CARET)
      """.stripMargin,
    resultText =
      s"""class FooBarBaz
         |def foo(barBaz: FooBarBaz$CARET)
      """.stripMargin,
    lookupString = "FooBarBaz",
    itemText = "barBaz: FooBarBaz"
  )

  def testImport(): Unit = doAotCompletionTest(
    fileText =
      s"""def foo(rectangle$CARET)
       """.stripMargin,
    resultText =
      s"""import java.awt.Rectangle
         |
         |def foo(rectangle: Rectangle$CARET)
      """.stripMargin,
    lookupString = "Rectangle",
    itemText = "rectangle: Rectangle",
    tailTextSuffix = "(java.awt)"
  )

  def testErasure(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |class Bar
         |def foo(ba${CARET}foo:  Foo): Unit = {}
       """.stripMargin,
    resultText =
      s"""class Foo
         |class Bar
         |def foo(bar: Bar$CARET): Unit = {}
       """.stripMargin,
    lookupString = "Bar",
    itemText = "bar: Bar"
  )

  def testDefaultPattern(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |
         |(_: Foo) match {
         |  case fo$CARET
         |}
       """.stripMargin,
    resultText =
      s"""class Foo
         |
         |(_: Foo) match {
         |  case foo: Foo$CARET
         |}
       """.stripMargin,
    lookupString = "Foo",
    itemText = "foo: Foo"
  )

  def testBeforeCase(): Unit = checkNoCompletion(
    fileText =
      s"""class Foo
         |
         |(_: Foo) match {
         |  $CARET
         |}
       """.stripMargin,
    item = "foo: Foo"
  )

  def testAfterArrow(): Unit = checkNoCompletion(
    fileText =
      s"""class Foo
         |
         |(_: Foo) match {
         |  case _ => $CARET
         |}
       """.stripMargin,
    item = "foo: Foo"
  )

  def testWildcard(): Unit = checkNoCompletion(
    fileText =
      s"""class Foo
         |
         |(_: Foo) match {
         |  case _$CARET
         |}
       """.stripMargin,
    item = "foo: Foo"
  )

  def testNamedPattern(): Unit = checkNoCompletion(
    fileText =
      s"""class Foo
         |
         |(_: Foo) match {
         |  case foo@f$CARET
         |}
       """.stripMargin,
    item = "foo: Foo"
  )

  private def doAotCompletionTest(fileText: String,
                                  resultText: String,
                                  lookupString: String,
                                  itemText: String,
                                  tailTextSuffix: String = ScTypeDefinitionImpl.DefaultLocationString): Unit = {
    val tailText = if (tailTextSuffix != null) " " + tailTextSuffix else null

    doCompletionTest(fileText, resultText, DEFAULT_CHAR, DEFAULT_TIME, DEFAULT_COMPLETION_TYPE) {
      hasItemText(_, lookupString, itemText, tailText)
    }
  }
}
