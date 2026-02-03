package org.jetbrains.plugins.scala.worksheet.integration.plain

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.plugins.scala.util.assertions.StringAssertions.assertStringMatches
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult.WorksheetRunError
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests.Folded
import org.jetbrains.plugins.scala.worksheet.integration.util.EditorRobot
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.persistent.WorksheetFilePersistentSettings
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterFactory
import org.junit.Test

import scala.concurrent.duration.DurationInt

trait WorksheetPlainIntegration_CommonTests extends PlainWorksheetTestBase
  with WorksheetRuntimeExceptionsTests with WorksheetPlainIntegration_HealthCheckTest {

  @Test
  def testSimple_2(): Unit = {
    val left =
      """val s = "Boo"
        |var b = 2
        |
        |class A {
        |  def foo = 1
        |}
        |
        |b = new A().foo
        |""".stripMargin

    val right =
      """s: String = Boo
        |b: Int = 2
        |
        |defined class A
        |
        |
        |
        |b: Int = 1""".stripMargin

    doRenderTest(left, right)
  }

  @Test
  def testTemplateDeclarations(): Unit = doRenderTest(
    """trait A {
      |}
      |trait B
      |abstract class C extends A
      |case class D(i: Int, s: String) extends C with B
      |object E extends B
      |
      |sealed trait Parent
      |case class Child1() extends Parent
      |case class Child2() extends Parent
      |object Child3 extends Parent
      |""".stripMargin,
    """defined trait A
      |
      |defined trait B
      |defined class C
      |defined class D
      |defined object E
      |
      |defined trait Parent
      |defined class Child1
      |defined class Child2
      |defined object Child3""".stripMargin
  )

  @Test
  def testTypeAlias(): Unit = doRenderTest(
    """class A[T] {
      |  def foo(t: T): T = t
      |}
      |
      |type B = A[String]
      |""".stripMargin,
    """defined class A
      |
      |
      |
      |defined type alias B""".stripMargin
  )

  @Test
  def testSimpleFolding(): Unit = {
    val left =
      """println("1\n2\n3")
        |val x = 42
        |""".stripMargin

    val right =
      s"""${foldStart}1
         |2
         |3
         |res0: Unit = ()$foldEnd
         |x: Int = 42""".stripMargin

    doRenderTest(left, right)
  }

  @Test
  def testMultipleFoldings(): Unit = {
    val left =
      """println("1\n2\n3")
        |val x = 42
        |println("4\n5\n6")
        |val y = 23
        |
        |val c = true
        |
        |if (c) {
        |  for (_ <- 1 to 10) println("boo!")
        |}
        |
        |val a = 123
        |
        |a match {
        |  case 1 =>
        |  case _ =>
        |}
        |""".stripMargin

    val right =
      s"""${foldStart}1
         |2
         |3
         |res0: Unit = ()$foldEnd
         |x: Int = 42
         |${foldStart}4
         |5
         |6
         |res1: Unit = ()$foldEnd
         |y: Int = 23
         |
         |c: Boolean = true
         |
         |boo!
         |boo!
         |${foldStart}boo!
         |boo!
         |boo!
         |boo!
         |boo!
         |boo!
         |boo!
         |boo!
         |res2: Unit = ()$foldEnd
         |
         |a: Int = 123
         |
         |res3: Unit = ()
         |
         |
         |""".stripMargin

    doRenderTest(left, right)
  }

  @Test
  def testFunctions(): Unit = doRenderTestWithoutCompilationWarningsChecks(
    """def foo() = 123
      |
      |def boo(i: Int) {
      |  for (_ <- 1 to i) println("boo!")
      |}
      |
      |def bar(s: String): Unit = println(s)
      |
      |def concat(s1: String, s2: String, s3: String) = s1 + s2 + s3
      |
      |val a: Int = foo()
      |boo(a)
      |bar("boo")
      |val s: String = concat("b", "o", "o")
      |""".stripMargin,
    s"""foo: foo[]() => Int
       |
       |boo: boo[](val i: Int) => Unit
       |
       |
       |
       |bar: bar[](val s: String) => Unit
       |
       |concat: concat[](val s1: String,val s2: String,val s3: String) => String
       |
       |a: Int = 123
       |${foldStart}boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |boo!
       |Output exceeds cutoff limit.$foldEnd
       |${foldStart}boo
       |res1: Unit = ()$foldEnd
       |s: String = boo""".stripMargin
  )

  private def doRenderTestWithoutCompilationWarningsChecks(): Unit = doRenderTest(
    """import java.util._
      |import java.lang.Math
      |
      |class A {
      |  import scala.collection.mutable._
      |
      |  def foo = HashMap[String, String]()
      |}
      |
      |def bar() {
      |  import java.io.File
      |  val f = new File("")
      |}
      |""".stripMargin,
    """import java.util._
      |import java.lang.Math
      |
      |defined class A
      |
      |
      |
      |
      |
      |bar: bar[]() => Unit
      |
      |
      |""".stripMargin
  )

  @Test
  def testDisplayFirstRuntimeException(): Unit = {
    val left =
      """println("1\n2")
        |
        |println(1 / 0)
        |
        |println(2 / 0)
        |""".stripMargin

    val right  =
      s"""${foldStart}1
         |2
         |res0: Unit = ()$foldEnd
         |
         |""".stripMargin

    val exceptionOutputAssert: String => Unit = text => {
      val stackTraceDepthLimit = WorksheetEditorPrinterFactory.BULK_COUNT
      assertStringMatches(
        text,
        ("\\Qjava.lang.ArithmeticException: / by zero\\E" +
          s"(\n\tat [^\n]*){1,$stackTraceDepthLimit}" +
          s"(\nOutput exceeds cutoff limit\\.)?").r
      )
    }


    val editor = testDisplayFirstRuntimeException(left, right, Folded(expanded = false), exceptionOutputAssert)
    // run again with same editor, the output should be the same between these runs
    testDisplayFirstRuntimeException(editor, right, Folded(expanded = false), exceptionOutputAssert)
  }

  @Test
  def testCompilationError(): Unit = {
    val before =
      """val x = new A()
        |
        |lazy val y = new B()
        |""".stripMargin

    val editorAndFile = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))
    //ATTENTION: note that even though we had simple `val` it's `lazy val` under the good
    // The logic is here: org.jetbrains.plugins.scala.worksheet.processor.WorksheetDefaultSourcePreprocessor.ScalaSourceBuilderBase.appendDeclaration
    // I don't 100% understand why it was introduced, but it was introduced withing changes for SCL-6752
    // Maybe we can drop this behaviour?
    assertCompilerMessages(editorAndFile.editor)(
      """Error:(1, 18) not found: type A
        |lazy val x = new A()
        |Error:(3, 18) not found: type B
        |lazy val y = new B()""".stripMargin
    )
  }

  @Test
  def testCompilationError_SomeCodeBeforeError(): Unit = {
    val before =
      """111
        |222
        |333
        |
        |val x = new A()
        |""".stripMargin

    val editorAndFile = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))
    assertCompilerMessages(editorAndFile.editor)(
      """Error:(5, 18) not found: type A
        |lazy val x = new A()""".stripMargin
    )
  }

  @Test
  def testCompilationError_NewLinesBeforeCode(): Unit = {
    val before =
      """
        |
        |val x = new A()
        |""".stripMargin

    val editorAndFile = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))
    assertCompilerMessages(editorAndFile.editor)(
      """Error:(3, 18) not found: type A
        |lazy val x = new A()""".stripMargin
    )
  }

  @Test
  def testCompilationError_NewLinesAndCommentBeforeCode(): Unit = {
    val before =
      """//line comment1
        |//line comment2
        |
        |val x = new A()
        |""".stripMargin

    val editorAndFile = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))
    assertCompilerMessages(editorAndFile.editor)(
      """Error:(4, 18) not found: type A
        |lazy val x = new A()
        |""".stripMargin
    )
  }

  @Test
  def testCompilationError_MultipleUnresolvedErrors(): Unit = {
    val before =
      """1
        |
        |
        |unresolved1
        |
        |
        |unresolved2
        |""".stripMargin

    val editorAndFile = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))
    assertCompilerMessages(editorAndFile.editor)(
      """Error:(4, 1) not found: value unresolved1
        |unresolved1
        |Error:(7, 1) not found: value unresolved2
        |unresolved2""".stripMargin
    )
  }

  @Test
  def testCompilationError_ContentIndentedInInnerScope(): Unit = {
    val before =
      """
        |class Outer {
        |  def foo = {
        |
        |    val x = new A()
        |  }
        |}
        |""".stripMargin

    val editorAndFile = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))
    assertCompilerMessages(editorAndFile.editor)(
      """Error:(5, 17) not found: type A
        |    val x = new A()
        |""".stripMargin
    )
  }

  @Test
  def testArrayRender(): Unit = {
    doRenderTest(
      """var a1 = new Array[Int](3)
        |val a2 = Array(1, 2, 3)""".stripMargin,
      """a1: Array[Int] = Array(0, 0, 0)
        |a2: Array[Int] = Array(1, 2, 3)""".stripMargin
    )
  }

  @Test
  def testInteractive(): Unit = {
    val editor = doRenderTest(
      """42""",
      """res0: Int = 42""".stripMargin
    ).editor
    val viewer = WorksheetCache.getInstance(project).getViewer(editor)
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetFilePersistentSettings(file.getVirtualFile).setInteractive(true)

    val robot = new EditorRobot(editor)
    robot.moveToEnd()
    robot.typeString("\n23\n")

    // TODO: this is not the best way of testing, cause it relies on lucky threading conditions,
    //  but current architecture doesn't allow us do it some other way, think how this can be improved
    val stamp = viewer.getDocument.getModificationStamp
    AwaitTestUtils.waitConditionedDispatchingAllEdtEvents(5.seconds) { () =>
      viewer.getDocument.getModificationStamp != stamp
    }

    assertViewerEditorText(editor,
      """res0: Int = 42
        |res1: Int = 23""".stripMargin
    )
    assertNoErrorMessages(editor)
  }

  @Test
  def testInteractive_WithError(): Unit = {
    val editor = doRenderTest(
      """42""",
      """res0: Int = 42""".stripMargin
    ).editor
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetFilePersistentSettings(file.getVirtualFile).setInteractive(true)

    val robot = new EditorRobot(editor)
    robot.moveToEnd()
    robot.typeString("\n2 + unknownRef + 4\n")

    // TODO: it shouldn't just wait for 5 seconds. WE need to a better way, a better condition to early break
    AwaitTestUtils.waitDispatchingAllEdtEvents(5.seconds)

    assertViewerEditorText(editor,
      """res0: Int = 42""".stripMargin
    )
    assertCompilerMessages(editor)(
      """Error:(2, 5) not found: value unknownRef
        |2 + unknownRef + 4""".stripMargin
    )
  }
}
