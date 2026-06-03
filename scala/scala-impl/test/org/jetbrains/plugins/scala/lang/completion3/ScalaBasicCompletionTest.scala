package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{CommonClassNames, JavaPsiFacade}
import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG, SELECTION_START_TAG}
import junit.framework.AssertionFailedError
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiNamedElementExt, inWriteAction, invokeAndWait}
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.api.{StdType, StdTypes}
import org.jetbrains.plugins.scala.util.ConfigureJavaFile.configureJavaFile
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}
import org.junit.Test

import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class ScalaBasicCompletionTestBase extends ScalaCompletionTestBase {

  protected override def setUp(): Unit = {
    super.setUp()
    scalaCompletionTestFixture.setCustomBeforeCompletionListener(() => {
      val offset = getEditor.getCaretModel.getOffset
      retypeLineAt(offset)
      scalaCompletionTestFixture.changePsiAt(offset)
    })
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

      val completionHandler = scalaCompletionTestFixture.createSynchronousCompletionHandler(autopopup = true)

      for (char <- lineStartText) {
        myFixture.`type`(char)
        scalaCompletionTestFixture.commitDocumentInEditor()

        completionHandler.invokeCompletion(getProject, editor, 0)
      }

      caretModel.moveToOffset(offset)

      println("Start of the line was retyped")
    }
  }

  private def hasOpeningBracesOrQuotes(text: String): Boolean =
    "{([<\"\'".exists(text.contains(_))
}

abstract class ScalaBasicCompletionTest_CommonTests extends ScalaBasicCompletionTestBase {

  import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase._

  @Test
  def testInImportSelector(): Unit = doCompletionTest(
    fileText = s"import scala.collection.immutable.{VBuil$CARET}",
    resultText = s"import scala.collection.immutable.{VectorBuilder$CARET}",
    item = "VectorBuilder"
  )

  @Test
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

  @Test
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

  @Test
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

  @Test
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
    invocationCount = 2
  )

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  def testAfterNewWithImport(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  val f = new LBuff$CARET
         |}
      """.stripMargin,
    resultText =
      s"""import scala.collection.mutable.ListBuffer
         |
         |class A {
         |  val f = new ListBuffer[$CARET]
         |}
      """.stripMargin,
    item = "ListBuffer",
    char = '['
  )

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
    invocationCount = 0
  )

  @Test
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

  @Test
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
    invocationCount = 0
  )

  @Test
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

  @Test
  def testYield(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Test extends App {
         |  Thread.$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Test extends App {
         |  Thread.`yield`()$CARET
         |}
         """.stripMargin,
    item = "`yield`"
  )

  @Test
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

  @Test
  def testNoPrefixedThis(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |class aaa {
         |  a$CARET
         |}
      """.stripMargin,
    item = "aaa.this"
  )

  @Test
  def testNoPrefixedSuper(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |class aaa {
         |  a$CARET
         |}
      """.stripMargin,
    item = "aaa.super"
  )

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  def testClassFileName(): Unit = doCompletionTest(
    fileText =
      s"""class a$CARET
         |""".stripMargin,
    resultText =
      s"""class aaa$CARET
         |""".stripMargin,
    item = "aaa"
  )

  @Test
  def testObjectFileName(): Unit = doCompletionTest(
    fileText =
      s"""class a$CARET
         |""".stripMargin,
    resultText =
      s"""class aaa$CARET
         |""".stripMargin,
    item = "aaa"
  )

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  def testTailrecBasicCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |class aaa {
         |  @tail$CARET
         |  def goo() {}
         |}
      """.stripMargin,
    resultText =
      s"""import scala.annotation.tailrec
         |
         |class aaa {
         |  @tailrec$CARET
         |  def goo() {}
         |}
      """.stripMargin,
    item = "tailrec"
  )

  @Test
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

  @Test
  def testSCL4837(): Unit = doCompletionTest(
    fileText = s"System.current$CARET()",
    resultText = s"System.currentTimeMillis()$CARET",
    item = "currentTimeMillis"
  )

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  def testBracketsExists(): Unit = doCompletionTest(
    fileText = s"clas$CARET[]",
    resultText = s"classOf[$CARET]",
    item = "classOf"
  )

  @Test
  def testBracketsExistsForType(): Unit = doRawCompletionTest(
    fileText = s"val x: Opti$CARET[]",
    resultText = s"val x: Option[$CARET]",
    char = '['
  ) { lookup =>
    hasLookupString(lookup, "Option") && lookup.getPsiElement.is[ScClass]
  }

  @Test
  def testBracketsWithoutParentheses(): Unit = doCompletionTest(
    fileText = s"Array.app$CARET",
    resultText = s"Array.apply[$CARET]",
    item = "apply",
    char = '['
  )

  @Test
  def testParenthesesCompletionChar(): Unit = doCompletionTest(
    fileText = s"System.c$CARET",
    resultText = s"System.currentTimeMillis($CARET)",
    item = "currentTimeMillis",
    char = '('
  )

  @Test
  def testNoEtaExpansion(): Unit = doCompletionTest(
    fileText = s"List(1, 2, 3) takeRight$CARET",
    resultText = s"List(1, 2, 3) takeRight $CARET",
    item = "takeRight",
    char = ' '
  )

  @Test
  def testNoEtaExpansionParenthesesCompletionChar(): Unit = doCompletionTest(
    fileText = s"List(1, 2, 3) takeRight$CARET",
    resultText = s"List(1, 2, 3) takeRight($CARET)",
    item = "takeRight",
    char = '('
  )

  @Test
  def testNoEtaExpansionBraceCompletionChar(): Unit = doCompletionTest(
    fileText = s"List(1, 2, 3) takeRight$CARET",
    resultText = s"List(1, 2, 3) takeRight {$CARET}",
    item = "takeRight",
    char = '{'
  )

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  def testCaseClassParamInValuePattern(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Person(name: String)
         |val Person(na$CARET) = null
       """.stripMargin,
    resultText =
      s"""
         |case class Person(name: String)
         |val Person(name$CARET) = null
         """.stripMargin,
    item = "name"
  )

  @Test
  def testCaseClassParamInCaseClause(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Person(name: String)
         |Person("Johnny") match {
         |  case Person(na$CARET) =>
         |}
      """.stripMargin,
    resultText =
      s"""
         |case class Person(name: String)
         |Person("Johnny") match {
         |  case Person(name$CARET) =>
         |}
         """.stripMargin,
    item = "name"
  )

  @Test
  def testCaseClassParamInGenerator(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Person(name: String)
         |val guys: List[Person] = ???
         |for (Person(na$CARET) <- guys) {}
      """.stripMargin,
    resultText =
      s"""
         |case class Person(name: String)
         |val guys: List[Person] = ???
         |for (Person(name$CARET) <- guys) {}
         """.stripMargin,
    item = "name"
  )

  @Test
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
      s"""import `interface`.ScalaClass
         |package `interface` {
         | class ScalaClass {
         |
         | }
         |}
         |
         |object Test {
         | new ScalaClass$CARET
         |}
       """.stripMargin,
    item = "ScalaClass"
  )

  @Test
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

  @Test
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

  private def getSyntheticClassMethodNames(stdType: StdType): Iterable[String] = {
    val syntheticClass = stdType.syntheticClass match {
      case Some(cls: ScSyntheticClass) => cls
      case _                           => throw new AssertionFailedError(s"No synthetic class for ${stdType.fullName} found")
    }

    syntheticClass.syntheticMethods.keySet().asScala
  }

  private def checkNoCompletion(fileText: String, items: Iterable[String]): Unit =
    items.foreach { item =>
      try checkNoBasicCompletion(
        fileText = fileText,
        item = item
      ) catch {
        case e: AssertionError => throw new AssertionError(s"Failed assertion for item: $item", e)
      }
    }

  @Test
  def testNoObjectMethodsOnPackageObject(): Unit = {
    val fileText =
      s"""package object foo {}
         |
         |import foo.$CARET
         |""".stripMargin

    val objectClass = JavaPsiFacade.getInstance(getProject)
      .findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(getProject))
    assertNotNull("No PsiClass for java.lang.Object found", objectClass)

    val objectMethods = objectClass.getMethods
      .filterNot(_.isConstructor)
      .map(_.name)
      .distinct

    checkNoCompletion(fileText, objectMethods)
  }

  @Test
  def testNoAnyRefMethodsOnPackageObject(): Unit = {
    val fileText =
      s"""package object foo {}
         |
         |import foo.$CARET
         |""".stripMargin

    val stdTypes = StdTypes.instance(getProject)
    val anyRefMethods = getSyntheticClassMethodNames(stdTypes.AnyRef)

    checkNoCompletion(fileText, anyRefMethods)
  }

  @Test
  def testNoAnyMethodsOnPackageObject(): Unit = {
    val fileText =
      s"""package object foo {}
         |
         |import foo.$CARET
         |""".stripMargin

    val stdTypes = StdTypes.instance(getProject)
    val anyMethods = getSyntheticClassMethodNames(stdTypes.Any)

    checkNoCompletion(fileText, anyMethods)
  }

  @Test
  def testPredefinedConversion(): Unit = doCompletionTest(
    fileText = s""""1".he$CARET""",
    resultText = s""""1".headOption$CARET""",
    item = "headOption"
  )

  @Test
  def testPredefinedConversionsCollision(): Unit = doCompletionTest(
    fileText = s"1.toBin$CARET",
    resultText = s"1.toBinaryString$CARET",
    item = "toBinaryString"
  )

  @Test
  def testParameterName(): Unit = checkNoBasicCompletion(
    fileText = s"def foo(bar: b$CARET)",
    item = "bar"
  )

  @Test
  def testLocalValueName(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo
         |
         |class A {
         |  def function(): Unit = {
         |    val foo = new f$CARET
         |  }
         |}
         |""".stripMargin,
    item = "foo"
  )

  @Test
  def testLocalValueName2(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class A {
         |  def function(): Unit = {
         |    val (foo, bar) = f$CARET
         |  }
         |}""".stripMargin,
    item = "foo"
  )

  @Test
  def testLocalValueName_WithTypeDefinition(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo
         |
         |class A {
         |  def function(): Unit = {
         |    val foo: String = f$CARET
         |  }
         |}
         |""".stripMargin,
    item = "foo"
  )

  @Test
  def testLocalValueName_InTypeAnnotation(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo
         |
         |class A {
         |  def function(): Unit = {
         |    val foo: f$CARET
         |  }
         |}
         |""".stripMargin,
    item = "foo"
  )

  @Test
  def testLocalValue_ClassField(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class A {
         |  val (foo, bar) = f$CARET
         |}
         |""".stripMargin,
    item = "foo"
  )

  @Test
  def testLocalLazyValueName(): Unit = doCompletionTest(
    fileText =
      s"""class Foo
         |
         |class A {
         |  def function(): Unit = {
         |    lazy val foo = f$CARET
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""class Foo
         |
         |class A {
         |  def function(): Unit = {
         |    lazy val foo = foo$CARET
         |  }
         |}
         |""".stripMargin,
    item = "foo"
  )

  @Test
  def testLocalValueName3(): Unit = checkNoBasicCompletion(
    fileText = s"val foo: f$CARET",
    item = "foo"
  )

  @Test
  def testClassParameter(): Unit = checkNoBasicCompletion(
    fileText = s"class Foo(val Som$CARET)",
    item = "Som"
  )

  @Test
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

  @Test
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

  @Test
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
         |    a.toString$CARET
         |  }
         |}
         |""".stripMargin,
    item = "toString"
  )

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
      s"""sealed trait ToInt[A] { def toInt(a: A): Int }
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

  @Test
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

  @Test
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
  @Test
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
         |    x.foo2$CARET
         |  }
         |}""".stripMargin,
    item = "foo2"
  )

  @Test
  def testJavaRawStackOverflowSCL24428(): Unit = {
    myFixture.addFileToProject("JavaRaw.java",
      """import java.lang.Comparable;
        |
        |interface ProcessorDefinition00<T extends ProcessorDefinition0> { }
        |abstract class ProcessorDefinition0<T extends ProcessorDefinition0<T>> implements ProcessorDefinition00 { abstract String foo0();}
        |abstract class ProcessorDefinition1<T extends ProcessorDefinition1<T>> implements Comparable<ProcessorDefinition1> { abstract int foo1();}
        |abstract class ProcessorDefinition2<T extends ProcessorDefinition2<T>> implements Comparable<Comparable<ProcessorDefinition2>> { abstract long foo2();}
        |abstract class ProcessorDefinition3<T extends ProcessorDefinition3<T>> implements Comparable<ProcessorDefinition3<?>> { abstract short foo3();}
        |""".stripMargin)

    doCompletionTest(
      fileText =
        s"""class Example {
           |  val value0: ProcessorDefinition0[_] = ???
           |  value0.$CARET
           |}
           |""".stripMargin,
      resultText =
        s"""class Example {
           |  val value0: ProcessorDefinition0[_] = ???
           |  value0.foo0()
           |}
           |""".stripMargin,
      item = "foo0"
    )

    doCompletionTest(
      fileText =
        s"""class Example {
           |  val value1: ProcessorDefinition1[_] = ???
           |  value1.$CARET
           |}
           |""".stripMargin,
      resultText =
        s"""class Example {
           |  val value1: ProcessorDefinition1[_] = ???
           |  value1.foo1()
           |}
           |""".stripMargin,
      item = "foo1"
    )

    doCompletionTest(
      fileText =
        s"""class Example {
           |  val value2: ProcessorDefinition2[_] = ???
           |  value2.$CARET
           |}
           |""".stripMargin,
      resultText =
        s"""class Example {
           |  val value2: ProcessorDefinition2[_] = ???
           |  value2.foo2()
           |}
           |""".stripMargin,
      item = "foo2"
    )

    doCompletionTest(
      fileText =
        s"""class Example {
           |  val value3: ProcessorDefinition3[_] = ???
           |  value3.$CARET
           |}
           |""".stripMargin,
      resultText =
        s"""class Example {
           |  val value3: ProcessorDefinition3[_] = ???
           |  value3.foo3()
           |}
           |""".stripMargin,
      item = "foo3"
    )
  }
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
class ScalaBasicCompletionTest_with_2_12 extends ScalaBasicCompletionTest_CommonTests

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
class ScalaBasicCompletionTest_with_2_13 extends ScalaBasicCompletionTest_CommonTests {
  @Test
  def testCompleteBackticksInBackticks(): Unit =
    for (char <- Seq(Lookup.NORMAL_SELECT_CHAR, Lookup.REPLACE_SELECT_CHAR))
      doCompletionTest(
        fileText =
          s"""object Test {
             |  def `bla ha` = 42
             |  println(`$CARET`)
             |}
             |""".stripMargin,
        resultText =
          s"""object Test {
             |  def `bla ha` = 42
             |  println(`bla ha`$CARET)
             |}
             |""".stripMargin,
        item = "`bla ha`",
        char = char,
      )

  @Test
  def testCompleteBackticksOutsideOfBackticks(): Unit =
    for (char <- Seq(Lookup.NORMAL_SELECT_CHAR, Lookup.REPLACE_SELECT_CHAR))
      doCompletionTest(
        fileText =
          s"""object Test {
             |  def `bla ha` = 42
             |  println($CARET)
             |}
             |""".stripMargin,
        resultText =
          s"""object Test {
             |  def `bla ha` = 42
             |  println(`bla ha`$CARET)
             |}
             |""".stripMargin,
        item = "`bla ha`",
        char = char,
      )

  @Test
  def testCompleteBackticksInStablePattern(): Unit =
    for (char <- Seq(Lookup.NORMAL_SELECT_CHAR, Lookup.REPLACE_SELECT_CHAR))
      doCompletionTest(
        fileText =
          s"""object Test {
             |  def `bla ha` = 42
             |  3 match {
             |    case `$CARET` =>
             |  }
             |}
             |""".stripMargin,
        resultText =
          s"""object Test {
             |  def `bla ha` = 42
             |  3 match {
             |    case `bla ha`$CARET =>
             |  }
             |}
             |""".stripMargin,
        item = "`bla ha`",
        char = char,
      )

  @Test
  def testCompleteBackticksInEmptyPatternPosition(): Unit =
    for (char <- Seq(Lookup.NORMAL_SELECT_CHAR, Lookup.REPLACE_SELECT_CHAR))
      doCompletionTest(
        fileText =
          s"""object Test {
             |  def `bla ha` = 42
             |  3 match {
             |    case $CARET =>
             |  }
             |}
             |""".stripMargin,
        resultText =
          s"""object Test {
             |  def `bla ha` = 42
             |  3 match {
             |    case `bla ha`$CARET =>
             |  }
             |}
             |""".stripMargin,
        item = "`bla ha`",
        char = char,
      )

  // SCL-15659
  @Test
  def testCompleteInStableIdentPattern(): Unit =
    for (char <- Seq(Lookup.NORMAL_SELECT_CHAR, Lookup.REPLACE_SELECT_CHAR))
      doCompletionTest(
        fileText =
          s"""object Test {
             |  val myValueName = 42
             |  23 match {
             |    case `my$CARET` =>
             |    case _ =>
             |  }
             |}
             |""".stripMargin,
        resultText =
          s"""object Test {
             |  val myValueName = 42
             |  23 match {
             |    case `myValueName`$CARET =>
             |    case _ =>
             |  }
             |}
             |""".stripMargin,
        item = "myValueName",
        char = char,
      )

  @Test
  def testExtensionMethodFromStandardLibrary_Scala213_1(): Unit = doCompletionTest(
    fileText = s""""".toInt$CARET""",
    resultText = s""""".toIntOption$CARET""",
    item = "toIntOption"
  )

  @Test
  def testExtensionMethodFromStandardLibrary_Scala213_2(): Unit = doCompletionTest(
    fileText = s"Nil.length$CARET",
    resultText = s"Nil.lengthIs$CARET",
    item = "lengthIs"
  )

  @Test
  def testMethodFromImplicitConversion(): Unit = doCompletionTest(
    s"""def test(): Unit = 1.unti$CARET""".stripMargin,
    s"""def test(): Unit = 1.until($CARET)""".stripMargin,
    item = "until"
  )

  @Test
  def testAbstractTypeCompanionInScala2_1(): Unit = checkNoBasicCompletion(
    s"""type Foo
       |object F$CARET""".stripMargin,
    "Foo")

  @Test
  def testAbstractTypeCompanionInScala2_2(): Unit = checkNoBasicCompletion(
    s"""object Foo
       |type F$CARET""".stripMargin,
    "Foo")
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class ScalaBasicCompletionTest_with_3_0 extends ScalaBasicCompletionTest_CommonTests {

  @Test
  def testEnumFileName(): Unit = doCompletionTest(
    fileText =
      s"""enum a$CARET
         |""".stripMargin,
    resultText =
      s"""enum aaa$CARET
         |""".stripMargin,
    item = "aaa"
  )

  @Test
  def testEnumCompanionTraitName(): Unit = checkNoBasicCompletion(
    fileText =
      s"""enum aaa
         |
         |object a$CARET
         |""".stripMargin,
    item = "aaa"
  )

  @Test
  def testAbstractTypeCompanion1(): Unit = doCompletionTest(
    s"""type Foo
       |object F$CARET""".stripMargin,
    s"""type Foo
       |object Foo$CARET""".stripMargin,
    "Foo")

  @Test
  def testAbstractTypeCompanion2(): Unit = doCompletionTest(
    s"""object Foo
       |type F$CARET""".stripMargin,
    s"""object Foo
       |type Foo$CARET""".stripMargin,
    "Foo")

  @Test
  def testOpaqueTypeCompanion1(): Unit = doCompletionTest(
    s"""opaque type Foo = Int
       |object F$CARET""".stripMargin,
    s"""opaque type Foo = Int
       |object Foo$CARET""".stripMargin,
    "Foo")

  @Test
  def testOpaqueTypeCompanion2(): Unit = doCompletionTest(
    s"""object Foo
       |opaque type F$CARET""".stripMargin,
    s"""object Foo
       |opaque type Foo$CARET""".stripMargin,
    "Foo")

  @Test
  def testMethodFromImplicitConversion(): Unit = doCompletionTest(
    s"""def test(): Unit = 1.unti$CARET""".stripMargin,
    s"""def test(): Unit = 1.until($CARET)""".stripMargin,
    item = "until"
  )

  @Test
  def testTypeAliasCompanion1(): Unit = checkNoBasicCompletion(
    s"""type Foo = Int
       |object F$CARET""".stripMargin,
    "Foo")

  @Test
  def testTypeAliasCompanion2(): Unit = checkNoBasicCompletion(
    s"""object Foo
       |type F$CARET = Int""".stripMargin,
    "Foo")

  @Test
  override def testNewInnerClass(): Unit = doCompletionTest(
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

  @Test
  override def testClassInPackageWithBackticks(): Unit = doCompletionTest(
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
      s"""import `interface`.ScalaClass
         |package `interface` {
         | class ScalaClass {
         |
         | }
         |}
         |
         |object Test {
         | new ScalaClass$CARET
         |}
      """.stripMargin,
    item = "ScalaClass"
  )

  @Test
  def testNewAfterDot(): Unit = doCompletionTest(
    fileText =
      s"""
         |object A {
         |  class CCCCC
         |}
         |
         |class B {
         |  new A.CCCC$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object A {
         |  class CCCCC
         |}
         |
         |class B {
         |  new A.CCCCC$CARET
         |}
      """.stripMargin,
    item = "CCCCC"
  )
}
