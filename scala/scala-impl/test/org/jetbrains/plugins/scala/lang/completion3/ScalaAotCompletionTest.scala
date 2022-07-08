package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.lookup.Lookup
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl

class ScalaAotCompletionTest extends ScalaCodeInsightTestBase {

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
    tailText = null
  )

  def testValueNameWithRhs(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |val f$CARET = new Foo
      """.stripMargin,
    resultText =
      s"""class Foo
         |val foo$CARET = new Foo
      """.stripMargin,
    lookupString = "Foo",
    itemText = "foo",
    tailText = null
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
    tailText = null
  )

  def testVariableNameWithRhs(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |var f$CARET = new Foo
      """.stripMargin,
    resultText =
      s"""class Foo
         |var foo$CARET = new Foo
      """.stripMargin,
    lookupString = "Foo",
    itemText = "foo",
    tailText = null
  )

  def testMethodName(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |
         |object Bar {
         |  def f$CARET
         |}""".stripMargin,
    resultText =
      s"""class Foo
         |
         |object Bar {
         |  def foo$CARET
         |}""".stripMargin,
    lookupString = "Foo",
    itemText = "foo",
    tailText = null
  )

  def testMethodNameWithRhs(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |
         |object Bar {
         |  def f$CARET = new Foo
         |}""".stripMargin,
    resultText =
      s"""class Foo
         |
         |object Bar {
         |  def foo$CARET = new Foo
         |}""".stripMargin,
    lookupString = "Foo",
    itemText = "foo",
    tailText = null
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
    tailText = "(java.awt)"
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

  def testNoErasure(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |class Bar
         |def foo(ba${CARET}foo: Foo): Unit = {}
       """.stripMargin,
    resultText =
      s"""class Foo
         |class Bar
         |def foo(bar: Bar${CARET}foo: Foo): Unit = {}
       """.stripMargin,
    lookupString = "Bar",
    itemText = "bar: Bar",
    char = Lookup.NORMAL_SELECT_CHAR
  )

  def testLambdaParameter(): Unit = doAotCompletionTest(
    fileText =
      s"""List.empty[String].foreach { s$CARET =>
         |}""".stripMargin,
    resultText =
      s"""List.empty[String].foreach { string$CARET =>
         |}""".stripMargin,
    lookupString = "String",
    itemText = "string",
    tailText = null
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

  def testBeforeCase(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo
         |
         |(_: Foo) match {
         |  $CARET
         |}
       """.stripMargin,
    item = "foo: Foo"
  )

  def testAfterArrow(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo
         |
         |(_: Foo) match {
         |  case _ => $CARET
         |}
       """.stripMargin,
    item = "foo: Foo"
  )

  def testWildcard(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo
         |
         |(_: Foo) match {
         |  case _$CARET
         |}
       """.stripMargin,
    item = "foo: Foo"
  )

  def testNamedPattern(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo
         |
         |(_: Foo) match {
         |  case foo@f$CARET
         |}
       """.stripMargin,
    item = "foo: Foo"
  )

  def testCaseClassParameters(): Unit = doAotCompletionTest(
    fileText =
      s"""class Foo
         |case class Bar(f$CARET)
       """.stripMargin,
    resultText =
      s"""class Foo
         |case class Bar(foo: Foo$CARET)
       """.stripMargin,
    lookupString = "Foo",
    itemText = "foo: Foo"
  )

  def testNoOverrideCompletion(): Unit = checkNoCompletion(
    fileText =
      s"""class Foo
         |
         |object Bar {
         |  override val f$CARET
         |}""".stripMargin
  ) {
    hasItemText(_, "Foo")(itemText = "foo")
  }

  private def doAotCompletionTest(fileText: String,
                                  resultText: String,
                                  lookupString: String,
                                  itemText: String,
                                  tailText: String = ScTypeDefinitionImpl.DefaultLocationString,
                                  char: Char = Lookup.REPLACE_SELECT_CHAR): Unit = {
    val grayed = tailText != null
    val fullTailText = if (grayed) " " + tailText else null

    doRawCompletionTest(fileText, resultText, char) {
      hasItemText(_, lookupString)(
        itemText = itemText,
        tailText = fullTailText,
        grayed = grayed
      )
    }
  }
}
