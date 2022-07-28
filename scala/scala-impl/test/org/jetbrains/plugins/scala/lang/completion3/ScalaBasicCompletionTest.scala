package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.lang.completion3.ScalaCodeInsightTestBase.hasItemText
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.util.ConfigureJavaFile.configureJavaFile
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.{assertEquals, assertTrue}

abstract class ScalaBasicCompletionTestBase extends ScalaCodeInsightTestBase {

  override protected def changePsiAt(offset: Int): Unit = {
    retypeLineAt(offset)
    super.changePsiAt(offset)
  }

  /**
   * Retypes line and invokes completion at every character.
   *
   * @param offset an offset in the document to invoke completion at.
   */
  private def retypeLineAt(offset: Int): Unit = invokeAndWait {
    val editor = getEditor
    val caretModel = editor.getCaretModel

    val document = editor.getDocument
    val lineStart = document.getLineStartOffset(document.getLineNumber(offset))

    val beforeLineStart = document.getText(TextRange.create(0, lineStart))
    val lineStartText = document.getText(TextRange.create(lineStart, offset))
    val afterCaret = document.getText(TextRange.create(offset, document.getTextLength))

    if (!hasOpeningBracesOrQuotes(lineStartText)) { //todo: disable typed handlers?
      inWriteAction {
        document.setText(beforeLineStart + afterCaret)
      }

      caretModel.moveToOffset(lineStart)

      val completionHandler = createSynchronousCompletionHandler(autopopup = true)

      for (char <- lineStartText) {
        myFixture.`type`(char)
        commitDocumentInEditor()

        completionHandler.invokeCompletion(getProject, editor, 0)
      }

      caretModel.moveToOffset(offset)

      println("Start of the line was retyped")
    }
  }

  private def hasOpeningBracesOrQuotes(text: String): Boolean =
    "{([<\"\'".exists(text.contains(_))
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12
))
class ScalaBasicCompletionTest extends ScalaBasicCompletionTestBase {

  import ScalaCodeInsightTestBase._

  def testInImportSelector(): Unit = doCompletionTest(
    fileText = s"import scala.collection.immutable.{VBuil$CARET}",
    resultText = s"import scala.collection.immutable.{VectorBuilder$CARET}",
    item = "VectorBuilder"
  )

  def testPrivateFromCompanionModule(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  A.$CARET
         |}
         |object A {
         |  private val xxxxx = 1
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  A.xxxxx$CARET
         |}
         |object A {
         |  private val xxxxx = 1
         |}
      """.stripMargin,
    item = "xxxxx"
  )

  def testVarCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  A.$CARET
         |}
         |object A {
         |  var xxxxx = 1
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  A.xxxxx$CARET
         |}
         |object A {
         |  var xxxxx = 1
         |}
      """.stripMargin,
    item = "xxxxx"
  )

  def testVarNoCompletion(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |class A {
         |  A.$CARET
         |}
         |object A {
         |  var xxxxx = 1
         |}
      """.stripMargin,
    item = "xxxxx_="
  )

  def testVarCompletion2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  A.$CARET
         |}
         |object A {
         |  var xxxxx = 1
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  A.xxxxx_=($CARET)
         |}
         |object A {
         |  var xxxxx = 1
         |}
      """.stripMargin,
    item = "xxxxx_=",
    time = 2
  )

  def testNewInnerClass(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  class BBBBB
         |  new BBBB$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  class BBBBB
         |  new BBBBB$CARET
         |}
      """.stripMargin,
    item = "BBBBB"
  )

  def testBeanProperty(): Unit = doCompletionTest(
    fileText =
      s"""import scala.beans.BeanProperty
         |abstract class Foo {
         |  def setGoo(foo : String) {}
         |}
         |
         |class Bar() extends Foo {
         |  @BeanProperty var goo = "foo"
         |}
         |new Bar().$CARET
         |""".stripMargin,
    resultText =
      s"""import scala.beans.BeanProperty
         |abstract class Foo {
         |  def setGoo(foo : String) {}
         |}
         |
         |class Bar() extends Foo {
         |  @BeanProperty var goo = "foo"
         |}
         |new Bar().getGoo$CARET
         |""".stripMargin,
    item = "getGoo"
  )

  def testSCL3546(): Unit = doCompletionTest(
    fileText =
      s"""
         |class C(private[this] val abcdef: Any)
         |new C(abcde$CARET = 0)
      """.stripMargin,
    resultText =
      s"""
         |class C(private[this] val abcdef: Any)
         |new C(abcdef$CARET = 0)
      """.stripMargin,
    item = "abcdef"
  )

  def testRecursion(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Main {
         |  class A {
         |    val brrrrr = 1
         |  }
         |
         |  class Z {
         |    def d = 1
         |    def d_=(x: Int) {}
         |  }
         |
         |  class C(a: A) extends Z {
         |    override var d = a.br$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Main {
         |  class A {
         |    val brrrrr = 1
         |  }
         |
         |  class Z {
         |    def d = 1
         |    def d_=(x: Int) {}
         |  }
         |
         |  class C(a: A) extends Z {
         |    override var d = a.brrrrr$CARET
         |  }
         |}
      """.stripMargin,
    item = "brrrrr"
  )

  def testObjectCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  val x: St$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  val x: States$CARET
         |}
      """.stripMargin,
    item = "States"
  )

  def testImportObjectCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  import St$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  import States$CARET
         |}
      """.stripMargin,
    item = "States"
  )

  def testObjectCompletionDotChar(): Unit = doCompletionTest(
    fileText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  val x: St$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  val x: States.$CARET
         |}
      """.stripMargin,
    item = "States",
    char = '.'
  )

  def testPrivateMethod(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  private def fooaa = 1
         |  def goo {
         |    foo$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  private def fooaa = 1
         |  def goo {
         |    fooaa$CARET
         |  }
         |}
      """.stripMargin,
    item = "fooaa"
  )

  def testParenthCompletionChar(): Unit = doCompletionTest(
    fileText =
      s"""
         |val theMap = Map()
         |th$CARET
      """.stripMargin,
    resultText =
      s"""
         |val theMap = Map()
         |theMap($CARET)
      """.stripMargin,
    item = "theMap",
    char = '('
  )

  def testAfterNew(): Unit = doCompletionTest(
    fileText =
      s"""
         |import collection.mutable.ListBuffer
         |class A {
         |  val f = new $CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import collection.mutable.ListBuffer
         |class A {
         |  val f = new ListBuffer[$CARET]
         |}
      """.stripMargin,
    item = "ListBuffer",
    char = '['
  )

  def testAfterNewWithImport(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  val f = new LBuff$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import scala.collection.mutable.ListBuffer
         |
         |class A {
         |  val f = new ListBuffer[$CARET]
         |}
      """.stripMargin,
    item = "ListBuffer",
    char = '['
  )

  def testSeq(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  val f = Se$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  val f = Seq($CARET)
         |}
      """.stripMargin,
    item = "Seq",
    char = '('
  )

  def testClosingParentheses(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  def foo(x: AnR$CARET)
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  def foo(x: AnyRef)$CARET
         |}
      """.stripMargin,
    item = "AnyRef",
    char = ')'
  )

  def testDeprecated(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  @dep$CARET
         |  def foo {}
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  @deprecated$CARET
         |  def foo {}
         |}
      """.stripMargin,
    item = "deprecated"
  )

  def testStringLength(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  "".len$CARET
         |}
      """.stripMargin,
    resultText =
      s"""class Foo {
         |  "".length$CARET
         |}
      """.stripMargin
  ) {
    hasItemText(_, "length")(
      itemTextBold = true,
      typeText = "Int",
    )
  }

  def testStringTrim(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  "".tri$CARET
         |}
      """.stripMargin,
    resultText =
      s"""class Foo {
         |  "".trim$CARET
         |}
      """.stripMargin
  ) {
    hasItemText(_, "trim")(
      itemTextBold = true,
      typeText = "String",
    )
  }

  def testStringHashCode(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  "".hash$CARET
         |}
      """.stripMargin,
    resultText =
      s"""class Foo {
         |  "".hashCode$CARET
         |}
      """.stripMargin
  ) {
    hasItemText(_, "hashCode")(
      itemTextBold = true,
      typeText = "Int",
    )
  }

  def testObjectHashCode(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  new Object().hash$CARET
         |}
      """.stripMargin,
    resultText =
      s"""class Foo {
         |  new Object().hashCode()$CARET
         |}
      """.stripMargin
  ) {
    hasItemText(_, "hashCode")(
      itemTextBold = true,
      tailText = "()",
      typeText = "Int",
    )
  }

  def testJavaMethod(): Unit = {
    configureJavaFile(
      fileText =
        s"""public class Foo {
           |  public int getFoo() {
           |    return 42;
           |  }
           |}""".stripMargin,
      className = "Foo"
    )

    doRawCompletionTest(
      fileText =
        s"""class Bar {
           |  new Foo().get$CARET
           |}
      """.stripMargin,
      resultText =
        s"""class Bar {
           |  new Foo().getFoo$CARET
           |}
      """.stripMargin
    ) {
      hasItemText(_, "getFoo")(
        itemTextBold = true,
        typeText = "Int",
      )
    }
  }

  def testParameterCompletion(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  def foo(bar: Int) {
         |    foo(b$CARET)
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""class Foo {
         |  def foo(bar: Int) {
         |    foo(bar$CARET)
         |  }
         |}
         |""".stripMargin,
  ) {
    hasItemText(_, "bar")(typeText = "Int")
  }

  def testNamedParameterCompletion(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  def foo(bar: Int) {
         |    foo(b$CARET)
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""class Foo {
         |  def foo(bar: Int) {
         |    foo(bar = $CARET)
         |  }
         |}
         |""".stripMargin
  ) {
    hasItemText(_, "bar")(
      tailText = " = ",
      typeText = "Int",
    )
  }

  def testHiding1(): Unit = doCompletionTest(
    fileText =
      s"""class SmartValueInitializerCompletion {
         |  def foo(x: Int) {}
         |  def foo(x: Boolean) {}
         |  def goo() {
         |    def foo(x: Int, y: Int) {}
         |    val x = 123
         |    f$CARET
         |  }
         |}""".stripMargin,
    resultText =
      s"""class SmartValueInitializerCompletion {
         |  def foo(x: Int) {}
         |  def foo(x: Boolean) {}
         |  def goo() {
         |    def foo(x: Int, y: Int) {}
         |    val x = 123
         |    foo($CARET)
         |  }
         |}""".stripMargin,
    item = "foo",
    time = 0
  )

  def testHiding2(): Unit = {
    configureFromFileText(
      fileText =
        s"""class SmartValueInitializerCompletion {
           |  def foo(x: Int) {}
           |  def foo(x: Boolean) {}
           |  f$CARET
           |  def goo() {
           |    def foo(x: Int, y: Int) {}
           |    val x = 123
           |  }
           |}""".stripMargin
    )

    val lookups = completeBasic(0)
    assertEquals(2, lookups.count(hasLookupString(_, "foo")))
  }

  def testHiding3(): Unit = doCompletionTest(
    fileText =
      s"""class SmartValueInitializerCompletion {
         |  val foo: Int = 1
         |  def goo(foo: Int) {
         |    f$CARET
         |  }
         |}""".stripMargin,
    resultText =
      s"""class SmartValueInitializerCompletion {
         |  val foo: Int = 1
         |  def goo(foo: Int) {
         |    foo$CARET
         |  }
         |}""".stripMargin,
    item = "foo",
    time = 0
  )

  def testBasicRenamed(): Unit = doCompletionTest(
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
    item = "BLLLL"
  )

  def testYield(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Test extends App {
         |  Thread.$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |object Test extends App {
        |  Thread.`yield`()
        |}
      """.stripMargin,
    item = "`yield`"
  )

  def testInfix(): Unit = doCompletionTest(
    fileText =
      s"""
         |class a {
         |  def foo(x: Int): Boolean = false
         |  false || this.fo$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class a {
         |  def foo(x: Int): Boolean = false
         |  false || this.foo($CARET)
         |}
      """.stripMargin,
    item = "foo"
  )

  def testNoPrefixedThis(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |class aaa {
         |  a$CARET
         |}
      """.stripMargin,
    item = "aaa.this"
  )

  def testNoPrefixedSuper(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |class aaa {
         |  a$CARET
         |}
      """.stripMargin,
    item = "aaa.super"
  )

  def testPrefixedThis(): Unit = doCompletionTest(
    fileText =
      s"""
         |class aaa {
         |  class bbb {
         |    a$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class aaa {
         |  class bbb {
         |    aaa.this$CARET
         |  }
         |}
      """.stripMargin,
    item = "aaa.this"
  )

  def testPrefixedSuper(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait ttt
         |class aaa extends ttt {
         |  class bbb {
         |    a$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |trait ttt
         |class aaa extends ttt {
         |  class bbb {
         |    aaa.super$CARET
         |  }
         |}
      """.stripMargin,
    item = "aaa.super"
  )

  def testNoPrefixedSuperOnQualifier(): Unit = checkNoCompletion(
    fileText =
      s"""
         |trait ttt
         |class aaa extends ttt {
         |  class bbb {
         |    1.a$CARET
         |  }
         |}
      """.stripMargin)(
    _.getLookupString.contains(".super")
  )


  def testCompanionTraitName(): Unit = doCompletionTest(
    fileText =
      s"""trait F$CARET
         |
         |object Foo
         |""".stripMargin,
    resultText =
      s"""trait Foo$CARET
         |
         |object Foo
         |""".stripMargin,
    item = "Foo"
  )

  def testCompanionObjectName(): Unit = doCompletionTest(
    fileText =
      s"""class Foo
         |
         |object F$CARET
         |""".stripMargin,
    resultText =
      s"""class Foo
         |
         |object Foo$CARET
         |""".stripMargin,
    item = "Foo"
  )

  def testClassFileName(): Unit = doCompletionTest(
    fileText =
      s"""class a$CARET
         |""".stripMargin,
    resultText =
      s"""class aaa$CARET
         |""".stripMargin,
    item = "aaa"
  )

  def testObjectFileName(): Unit = doCompletionTest(
    fileText =
      s"""class a$CARET
         |""".stripMargin,
    resultText =
      s"""class aaa$CARET
         |""".stripMargin,
    item = "aaa"
  )

  def testNoBeanCompletion(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |class Foo {
         |  val bar = 10
         |}
         |
         |new Foo().$CARET
      """.stripMargin,
    item = "getBar"
  )

  def testBasicTypeCompletion(): Unit = doCompletionTest(
    fileText =
      s"""class Foo {
         |  val bar: Int$CARET
         |}""".stripMargin,
    resultText =
      s"""class Foo {
         |  val bar: Int$CARET
         |}""".stripMargin,
    item = "Int"
  )

  def testCompanionObjectWithPackage(): Unit = doCompletionTest(
    fileText =
      s"""package foo
         |
         |class Foo {
         |  import F$CARET
         |}
         |
         |object Foo
       """.stripMargin,
    resultText =
      s"""package foo
         |
         |class Foo {
         |  import Foo$CARET
         |}
         |
         |object Foo
       """.stripMargin,
    item = "Foo"
  )

  def testObjectsCompletion(): Unit = {
    configureFromFileText(
      s"""object Main {
         |  case class Foo()
         |
         |  trait Bar
         |  object Bar
         |  trait Bar2
         |
         |  class Baz
         |  object Baz
         |  class Baz2
         |
         |  object BarBaz
         |
         |  Main.$CARET
         |}
       """.stripMargin
    )

    val lookups = myFixture.completeBasic()
    for {
      lookupString <- "Foo" :: "Bar" :: "Baz" :: "BarBaz" :: Nil
      actual = lookups.count(hasLookupString(_, lookupString))
    } assertEquals(1, actual)
  }

  def testBasicTypeCompletionNoMethods(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |class Foo {
         |  def foo(): Int = 1
         |
         |  val bar: $CARET
         |}
      """.stripMargin,
    item = "foo"
  )

  def testBraceCompletionChar(): Unit = doCompletionTest(
    fileText =
      s"""
         |class aaa {
         |  Seq(1, 2, 3).ma$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class aaa {
         |  Seq(1, 2, 3).map {$CARET}
         |}
      """.stripMargin,
    item = "map",
    char = '{'
  )

  def testBraceCompletionChar2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class aaa {
         |  Seq(1, 2, 3).ma$CARET {}
         |}
      """.stripMargin,
    resultText =
      s"""
         |class aaa {
         |  Seq(1, 2, 3).map {$CARET}
         |}
      """.stripMargin,
    item = "map",
    char = '{'
  )

  def testBraceCompletionChar3(): Unit = doCompletionTest(
    fileText =
      s"""
         |class aaa {
         |  Seq(1, 2, 3).ma$CARET{}
         |}
      """.stripMargin,
    resultText =
      s"""
         |class aaa {
         |  Seq(1, 2, 3).map {$CARET}
         |}
      """.stripMargin,
    item = "map",
    char = '{'
  )

  def testTailrecBasicCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |class aaa {
         |  @tail$CARET
         |  def goo() {}
         |}
      """.stripMargin,
    resultText =
      s"""
         |import scala.annotation.tailrec
         |
         |class aaa {
         |  @tailrec$CARET
         |  def goo() {}
         |}
      """.stripMargin,
    item = "tailrec"
  )

  def testSCL4791(): Unit = doCompletionTest(
    fileText =
      s"""
         |object PrivateInvisible {
         |  trait Requirement
         |
         |  trait Test {
         |    needs: Requirement =>
         |
         |    private def fault: Int = 7
         |    def work() {
         |      // Typing "fa", word "fault" is not present in the completion list
         |      val z = fa$CARET
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |object PrivateInvisible {
         |  trait Requirement
         |
         |  trait Test {
         |    needs: Requirement =>
         |
         |    private def fault: Int = 7
         |    def work() {
         |      // Typing "fa", word "fault" is not present in the completion list
         |      val z = fault$CARET
         |    }
         |  }
         |}
      """.stripMargin,
    item = "fault"
  )

  def testSCL4837(): Unit = doCompletionTest(
    fileText = s"System.current$CARET()",
    resultText = s"System.currentTimeMillis()$CARET",
    item = "currentTimeMillis"
  )

  def testParenthesisExists(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Int) = 1
         |fo$CARET()
      """.stripMargin,
    resultText =
      s"""
         |def foo(x: Int) = 1
         |foo($CARET)
      """.stripMargin,
    item = "foo"
  )

  def testParenthesisExists2(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Int) = 1
         |fo$CARET ()
      """.stripMargin,
    resultText =
      s"""
         |def foo(x: Int) = 1
         |foo ($CARET)
      """.stripMargin,
    item = "foo"
  )

  def testParenthesesExistBraceCompletionChar2(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Int) = 1
         |fo$CARET ()
      """.stripMargin,
    resultText =
      s"""
         |def foo(x: Int) = 1
         |foo {$CARET} ()
      """.stripMargin,
    item = "foo",
    char = '{'
  )

  def testBracesExists(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Int) = 1
         |fo$CARET{}
      """.stripMargin,
    resultText =
      s"""
         |def foo(x: Int) = 1
         |foo {$CARET}
      """.stripMargin,
    item = "foo"
  )

  def testBracesExists2(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Int) = 1
         |fo$CARET {}
      """.stripMargin,
    resultText =
      s"""
         |def foo(x: Int) = 1
         |foo {$CARET}
      """.stripMargin,
    item = "foo"
  )

  def testBracesExists3(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Int) = 1
         |fo$CARET {
         |
         |}
      """.stripMargin,
    resultText =
      s"""
         |def foo(x: Int) = 1
         |foo {$CARET
         |
         |}
      """.stripMargin,
    item = "foo"
  )

  def testBracesExistsParenthesesCompletionChar(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Int) = 1
         |fo$CARET{}
      """.stripMargin,
    resultText =
      s"""
         |def foo(x: Int) = 1
         |foo($CARET){}
      """.stripMargin,
    item = "foo",
    char = '('
  )

  def testBracesExistsParenthesesCompletionChar2(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Int) = 1
         |fo$CARET {}
      """.stripMargin,
    resultText =
      s"""
         |def foo(x: Int) = 1
         |foo($CARET) {}
      """.stripMargin,
    item = "foo",
    char = '('
  )

  def testBracketsExists(): Unit = doCompletionTest(
    fileText = s"clas$CARET[]",
    resultText = s"classOf[$CARET]",
    item = "classOf"
  )

  def testBracketsExistsForType(): Unit = doRawCompletionTest(
    fileText = s"val x: Opti$CARET[]",
    resultText = s"val x: Option[$CARET]",
    char = '['
  ) { lookup =>
    hasLookupString(lookup, "Option") && lookup.getPsiElement.isInstanceOf[ScClass]
  }

  def testBracketsWithoutParentheses(): Unit = doCompletionTest(
    fileText = s"Array.app$CARET",
    resultText = s"Array.apply[$CARET]",
    item = "apply",
    char = '['
  )

  def testParenthesesCompletionChar(): Unit = doCompletionTest(
    fileText = s"System.c$CARET",
    resultText = s"System.currentTimeMillis($CARET)",
    item = "currentTimeMillis",
    char = '('
  )

  def testNoEtaExpansion(): Unit = doCompletionTest(
    fileText = s"List(1, 2, 3) takeRight$CARET",
    resultText = s"List(1, 2, 3) takeRight $CARET",
    item = "takeRight",
    char = ' '
  )

  def testNoEtaExpansionParenthesesCompletionChar(): Unit = doCompletionTest(
    fileText = s"List(1, 2, 3) takeRight$CARET",
    resultText = s"List(1, 2, 3) takeRight($CARET)",
    item = "takeRight",
    char = '('
  )


  def testNoEtaExpansionBraceCompletionChar(): Unit = doCompletionTest(
    fileText = s"List(1, 2, 3) takeRight$CARET",
    resultText = s"List(1, 2, 3) takeRight {$CARET}",
    item = "takeRight",
    char = '{'
  )

  def testTypeIsFirst(): Unit = {
    val (_, items) = activeLookupWithItems(
      fileText =
        s"""class A {
           |  def typeSomething = 1
           |
           |  type$CARET
           |""".stripMargin,
      itemsExtractor = lookup => Option(lookup.getCurrentItem) // getCurrentItem is nullable
    )

    assertTrue(items.exists(hasLookupString(_, "type")))
  }

  def testBackticks(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  def `foo` = 123
         |
         |  `f$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  def `foo` = 123
         |
         |  `foo`$CARET
         |}
      """.stripMargin,
    item = "`foo`"
  )

  def testStringSimple(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  val xxx = 1
         |  "$$$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  val xxx = 1
         |  s"$$xxx$CARET"
         |}
      """.stripMargin,
    item = "xxx"
  )

  def testStringSimpleFunctionParameter(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  def xxx(yyy: Int) = "$$$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  def xxx(yyy: Int) = s"$$yyy$CARET"
         |}
      """.stripMargin,
    item = "yyy"
  )

  def testStringNeedBraces(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  val xxx = 1
         |  "$$${CARET}asdfas"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  val xxx = 1
         |  s"$${xxx$CARET}asdfas"
         |}
      """.stripMargin,
    item = "xxx",
    char = '\n'
  )

  def testStringFunction(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  def xxx() = 1
         |  "$$$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  def xxx() = 1
         |  s"$${xxx()$CARET}"
         |}
      """.stripMargin,
    item = "xxx"
  )

  def testInterpolatedStringDotCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  def xxx: String = "abc"
         |  s"$$xxx.$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  def xxx: String = "abc"
         |  s"$${xxx.substring($CARET)}"
         |}
      """.stripMargin,
    item = "substring"
  )

  def testInterpolatedStringDotCompletionBracesExist(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  def xxx: String = "abc"
         |  s"$${xxx.$CARET {}}"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  def xxx: String = "abc"
         |  s"$${xxx.substring {$CARET}}"
         |}
      """.stripMargin,
    item = "substring"
  )

  def testInterpolatedStringDotCompletion2(): Unit = doCompletionTest(
    fileText =
      s"""class Foo {
         |  def f = 42
         |}
         |
         |object Foo {
         |  val foo = new Foo
         |
         |  s"foo$$foo.$CARET"
         |}""".stripMargin,
    resultText =
      s"""class Foo {
         |  def f = 42
         |}
         |
         |object Foo {
         |  val foo = new Foo
         |
         |  s"foo$${foo.f$CARET}"
         |}""".stripMargin,
    item = "f"
  )

  def testMakeStringInterpolated(): Unit = doCompletionTest(
    fileText =
      s"""object Test {
         |  val abc = "abc"
         |  "foo$$ab$CARET"
         |}
         |""".stripMargin,
    resultText =
      s"""object Test {
         |  val abc = "abc"
         |  s"foo$$abc$CARET"
         |}
         |""".stripMargin,
    item = "abc"
  )

  def testCaseClassParamInValuePattern(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Person(name: String)
         |val Person(na$CARET) = null
       """.stripMargin,
    resultText =
      """
        |case class Person(name: String)
        |val Person(name) = null
      """.stripMargin,
    item = "name"
  )

  def testCaseClassParamInCaseClause(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Person(name: String)
         |Person("Johnny") match {
         |  case Person(na$CARET) =>
         |}
      """.stripMargin,
    resultText =
      """
        |case class Person(name: String)
        |Person("Johnny") match {
        |  case Person(name) =>
        |}
      """.stripMargin,
    item = "name"
  )

  def testCaseClassParamInGenerator(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Person(name: String)
         |val guys: List[Person] = ???
         |for (Person(na$CARET) <- guys) {}
      """.stripMargin,
    resultText =
      """
        |case class Person(name: String)
        |val guys: List[Person] = ???
        |for (Person(name) <- guys) {}
      """.stripMargin,
    item = "name"
  )

  def testClassInPackageWithBackticks(): Unit = doCompletionTest(
    fileText =
      s"""
         |package `interface` {
         | class ScalaClass {
         |
         | }
         |}
         |
         |object Test {
         | new ScalaC$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import `interface`.ScalaClass
        |package `interface` {
        | class ScalaClass {
        |
        | }
        |}
        |
        |object Test {
        | new ScalaClass
        |}
      """.stripMargin,
    item = "ScalaClass"
  )

  def testMirror(): Unit = doCompletionTest(
    fileText =
      s"""object Main {
         |
         |  class Foo {
         |    def bar(int: Int): Unit = {}
         |  }
         |
         |  val foo = new Foo
         |  foo.$CARET
         |  foo.bar(42)
         |}
       """.stripMargin,
    resultText =
      s"""object Main {
         |
         |  class Foo {
         |    def bar(int: Int): Unit = {}
         |  }
         |
         |  val foo = new Foo
         |  foo.bar($CARET)
         |  foo.bar(42)
         |}
       """.stripMargin,
    item = "bar"
  )

  def testPackageObject(): Unit = doCompletionTest(
    fileText =
      s"""package object foo {
         |  class Foo
         |}
         |
         |import foo.$CARET
       """.stripMargin,
    s"""package object foo {
       |  class Foo
       |}
       |
       |import foo.Foo$CARET
     """.stripMargin,
    item = "Foo"
  )

  def testPredefinedConversion(): Unit = doCompletionTest(
    fileText = s""""1".he$CARET""",
    resultText = s""""1".headOption$CARET""",
    item = "headOption"
  )

  def testPredefinedConversionsCollision(): Unit = doCompletionTest(
    fileText = s"1.toBin$CARET",
    resultText = s"1.toBinaryString$CARET",
    item = "toBinaryString"
  )

  def testParameterName(): Unit = checkNoBasicCompletion(
    fileText = s"def foo(bar: b$CARET)",
    item = "bar"
  )

  def testLocalValueName(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo
         |
         |val foo = new f$CARET
         |""".stripMargin,
    item = "foo"
  )

  def testLocalValueName2(): Unit = checkNoBasicCompletion(
    fileText = s"val (foo, bar) = f$CARET",
    item = "foo"
  )

  def testLocalValueName3(): Unit = checkNoBasicCompletion(
    fileText = s"val foo: f$CARET",
    item = "foo"
  )

  def testClassParameter(): Unit = checkNoBasicCompletion(
    fileText = s"class Foo(val Som$CARET)",
    item = "Som"
  )

  def testConstructorPatternValueName(): Unit = doCompletionTest(
    fileText =
      s"""Array.emptyObjectArray match {
         |  case Array(head) => h$CARET
         |}""".stripMargin,
    resultText =
      s"""Array.emptyObjectArray match {
         |  case Array(head) => head$CARET
         |}""".stripMargin,
    item = "head"
  )

  def testThisTypeDependentType(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Abc {
         |  trait Type
         |}
         |
         |class Foo(val abc: Abc) {
         |  private def baz(): Int = {
         |    val a: Foo.this.abc.T$CARET = ???
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""
         |class Abc {
         |  trait Type
         |}
         |
         |class Foo(val abc: Abc) {
         |  private def baz(): Int = {
         |    val a: Foo.this.abc.Type$CARET = ???
         |  }
         |}
         |""".stripMargin,
    item = "Type"
  )

  def testThisTypeDependentType2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Abc {
         |  trait Type
         |}
         |
         |class Foo(val abc: Abc) {
         |  private def baz(): Int = {
         |    val a: Foo.this.abc.Type = ???
         |    a.toS$CARET
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""
         |class Abc {
         |  trait Type
         |}
         |
         |class Foo(val abc: Abc) {
         |  private def baz(): Int = {
         |    val a: Foo.this.abc.Type = ???
         |    a.toString
         |  }
         |}
         |""".stripMargin,
    item = "toString"
  )

  def testSuperTypeDependentType(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait Abc {
         |  type Type
         |}
         |
         |class Foo extends Abc {
         |  type Type = Int
         |
         |  private def baz(): String = {
         |    val a: Foo.super.Type = ???
         |    a.toS$CARET
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""
         |trait Abc {
         |  type Type
         |}
         |
         |class Foo extends Abc {
         |  type Type = Int
         |
         |  private def baz(): String = {
         |    val a: Foo.super.Type = ???
         |    a.toString$CARET
         |  }
         |}
         |""".stripMargin,
    item = "toString"
  )

  def testCompletionAfterDotNotLastInBlock(): Unit = doCompletionTest(
    fileText =
      s"""class TestClass {
         |  def unitReturnFunc: Unit = {
         |    val testValue = ""
         |    testValue.$CARET
         |    ()
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""class TestClass {
         |  def unitReturnFunc: Unit = {
         |    val testValue = ""
         |    testValue.charAt($CARET)
         |    ()
         |  }
         |}
         |""".stripMargin,
    item = "charAt"
  )

  def testCompletionAfterDotNotLastInBlock2(): Unit = doCompletionTest(
    fileText =
      s"""class TestClass {
         |  def unitReturnFunc: Unit = {
         |    val testValue = ""
         |    testValue.$CARET
         |    {}
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""class TestClass {
         |  def unitReturnFunc: Unit = {
         |    val testValue = ""
         |    testValue.charAt($CARET)
         |    {}
         |  }
         |}
         |""".stripMargin,
    item = "charAt"
  )

  def testGetter(): Unit = doCompletionTest(
    fileText =
      s"""def foo: Int = ???
         |
         |f$CARET""".stripMargin,
    resultText =
      s"""def foo: Int = ???
         |
         |foo$CARET""".stripMargin,
    item = "foo"
  )

  def testSetter(): Unit = doRawCompletionTest(
    fileText =
      s"""def foo: Int = ???
         |def foo_=(foo: Int): Unit = {}
         |
         |f$CARET""".stripMargin,
    resultText =
      s"""def foo: Int = ???
         |def foo_=(foo: Int): Unit = {}
         |
         |foo = $CARET""".stripMargin
  ) {
    hasItemText(_, "foo")(
      tailText = " = (foo: Int)",
      typeText = "Unit",
    )
  }

  def testConversionWithImplicitParameter(): Unit = doCompletionTest(
    fileText =
      s"""sealed trait ToInt[A] { def toInt(a: A): Int }
         |object ToInt {
         |  implicit val int: ToInt[Int] = ???
         |}
         |object test extends App {
         |  implicit class OptSyntax[A](o: Option[A])(implicit onlyIntImplicit: ToInt[A]) {
         |    def asX: Int = ???
         |  }
         |  val s: Option[Int] = ???
         |  s.as$CARET
         |}""".stripMargin,
    resultText =
      s"""
         |sealed trait ToInt[A] { def toInt(a: A): Int }
         |object ToInt {
         |  implicit val int: ToInt[Int] = ???
         |}
         |object test extends App {
         |  implicit class OptSyntax[A](o: Option[A])(implicit onlyIntImplicit: ToInt[A]) {
         |    def asX: Int = ???
         |  }
         |  val s: Option[Int] = ???
         |  s.asX$CARET
         |}""".stripMargin,
    item = "asX"
  )

  def testNoConversionWithoutImplicitParameter(): Unit = checkNoBasicCompletion(
    fileText =
      s"""sealed trait ToInt[A] { def toInt(a: A): Int }
         |object ToInt {
         |  implicit val int: ToInt[Int] = ???
         |}
         |object test extends App {
         |  implicit class OptSyntax[A](o: Option[A])(implicit onlyIntImplicit: ToInt[A]) {
         |    def asX: Int = ???
         |  }
         |  val s: Option[String] = ???
         |  s.as$CARET
         |}""".stripMargin,
    item = "asX"
  )

  def testConversionWithImplicitParameter2(): Unit = doCompletionTest(
    fileText =
      s"""object Test {
         |  trait A
         |  trait B {
         |    def bMethod: Unit = ???
         |  }
         |
         |  trait Builder[From, To] {
         |    def buildFrom(x: From): To
         |  }
         |
         |  implicit val a2bBuilder: Builder[A, B] = ???
         |
         |  implicit def a2b[From, To >: B](x: From)(implicit bl: Builder[From, To]): To = bl.buildFrom(x)
         |
         |  val a: A = ???
         |  a.b$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""object Test {
         |  trait A
         |  trait B {
         |    def bMethod: Unit = ???
         |  }
         |
         |  trait Builder[From, To] {
         |    def buildFrom(x: From): To
         |  }
         |
         |  implicit val a2bBuilder: Builder[A, B] = ???
         |
         |  implicit def a2b[From, To >: B](x: From)(implicit bl: Builder[From, To]): To = bl.buildFrom(x)
         |
         |  val a: A = ???
         |  a.bMethod$CARET
         |}
         |""".stripMargin,
    item = "bMethod"
  )

  //SCL-19124
  def testConversionWithImplicitParameter3(): Unit = doCompletionTest(
    s"""
       |import scala.language.implicitConversions
       |trait DoubleParam[F[_], SubParam] {
       |  def foo2 = true
       |}
       |final class DoubleParamOps[F[_], SubParam, Val](private val p: F[Val])
       |  extends AnyVal {
       |  def foo2(implicit F: DoubleParam[F, SubParam]) = F.foo2
       |}
       |
       |object mySyntax {
       |  implicit def syntaxDoubleParam[F[_], SubParam, Val](
       |                                                       p: F[Val]
       |                                                     )(implicit F: DoubleParam[F, SubParam]) = {
       |    new DoubleParamOps[F, SubParam, Val](p)
       |  }
       |}
       |class Main {
       |  import mySyntax._
       |  def f2[F[_]](x: F[Int])(implicit F: DoubleParam[F, Throwable]) = {
       |    x.fo$CARET
       |  }
       |}""".stripMargin,
    resultText =
      s"""
         |import scala.language.implicitConversions
         |trait DoubleParam[F[_], SubParam] {
         |  def foo2 = true
         |}
         |final class DoubleParamOps[F[_], SubParam, Val](private val p: F[Val])
         |  extends AnyVal {
         |  def foo2(implicit F: DoubleParam[F, SubParam]) = F.foo2
         |}
         |
         |object mySyntax {
         |  implicit def syntaxDoubleParam[F[_], SubParam, Val](
         |                                                       p: F[Val]
         |                                                     )(implicit F: DoubleParam[F, SubParam]) = {
         |    new DoubleParamOps[F, SubParam, Val](p)
         |  }
         |}
         |class Main {
         |  import mySyntax._
         |  def f2[F[_]](x: F[Int])(implicit F: DoubleParam[F, Throwable]) = {
         |    x.foo2
         |  }
         |}""".stripMargin,
    item = "foo2")
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
class ScalaBasicCompletionTest_with_2_13_extensionMethods extends ScalaBasicCompletionTestBase {

  def test2_13_extensionMethod1(): Unit = doCompletionTest(
    fileText = s""""".toInt$CARET""",
    resultText = s""""".toIntOption$CARET""",
    item = "toIntOption"
  )

  def test2_13_extensionMethod2(): Unit = doCompletionTest(
    fileText = s"Nil.length$CARET",
    resultText = s"Nil.lengthIs$CARET",
    item = "lengthIs"
  )

  def testMethodFromImplicitConversion(): Unit = doCompletionTest(
    s"""def test(): Unit = 1.unti$CARET""".stripMargin,
    """def test(): Unit = 1.until()""".stripMargin,
    item = "until"
  )
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class ScalaBasicCompletionTest_with_3_0 extends ScalaBasicCompletionTest {

  def testEnumFileName(): Unit = doCompletionTest(
    fileText =
      s"""enum a$CARET
         |""".stripMargin,
    resultText =
      s"""enum aaa$CARET
         |""".stripMargin,
    item = "aaa"
  )

  def testEnumCompanionTraitName(): Unit = checkNoBasicCompletion(
    fileText =
      s"""enum aaa
         |
         |object a$CARET
         |""".stripMargin,
    item = "aaa"
  )

  def testMethodFromImplicitConversion(): Unit = doCompletionTest(
    s"""def test(): Unit = 1.unti$CARET""".stripMargin,
    """def test(): Unit = 1.until()""".stripMargin,
    item = "until"
  )

  //todo why there is a difference in "boldness"?
  override def testStringTrim(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  "".tri$CARET
         |}
      """.stripMargin,
    resultText =
      s"""class Foo {
         |  "".trim$CARET
         |}
      """.stripMargin
  ) {
    hasItemText(_, "trim")(
      itemTextBold = false,
      typeText = "String",
    )
  }

  override def testStringLength(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  "".len$CARET
         |}
      """.stripMargin,
    resultText =
      s"""class Foo {
         |  "".length$CARET
         |}
      """.stripMargin
  ) {
    hasItemText(_, "length")(
      itemTextBold = false,
      typeText = "Int",
    )
  }


  override def testStringHashCode(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  "".hash$CARET
         |}
      """.stripMargin,
    resultText =
      s"""class Foo {
         |  "".hashCode$CARET
         |}
      """.stripMargin
  ) {
    hasItemText(_, "hashCode")(
      itemTextBold = false,
      typeText = "Int",
    )
  }

  override def testLocalValueName(): Unit = failing(super.testLocalValueName())

  override def testLocalValueName2(): Unit = failing(super.testLocalValueName2())
}
