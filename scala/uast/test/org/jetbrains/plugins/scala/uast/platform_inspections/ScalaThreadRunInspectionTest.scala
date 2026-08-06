package org.jetbrains.plugins.scala.uast.platform_inspections

import com.intellij.codeInspection.ThreadRunInspection
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class ScalaThreadRunInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[ThreadRunInspection]
  override protected val description = "Calls to 'run()' should probably be replaced with 'start()'"
  private val hint = "Replace with 'start()'"

  def testHighlighting(): Unit = checkTextHasError(
    s"""thread.${START}run$END()
       |thread.${START}run$END
       |thread ${START}run$END()
       |thread ${START}run$END
       |Option(thread).foreach(t => t.${START}run$END())
       |Option(thread).foreach(t => t.${START}run$END)
       |Option(thread).foreach(t => t ${START}run$END())
       |Option(thread).foreach(t => t ${START}run$END)
       |""".stripMargin
  )

  def testNoHighlightingSuper(): Unit = checkTextHasNoErrors(
    """new Thread("") {
      |  override def run(): Unit = super.run()
      |}""".stripMargin
  )

  def testQuickFixSimple(): Unit =
    testQuickFix("thread.run()", "thread.start()", hint)

  def testQuickfixPreserveWhitespaces(): Unit =
    testQuickFix(
      """thread
        |    .run()""".stripMargin,
      """thread
        |    .start()""".stripMargin,
      hint
    )

  def testQuickFixWithoutParens(): Unit =
    testQuickFix("thread.run", "thread.start()", hint)

  def testQuickFixWithoutDot(): Unit =
    testQuickFix("thread run()", "thread.start()", hint)

  def testQuickFixWithoutDotAndParens(): Unit =
    testQuickFix("thread run", "thread.start()", hint)

  def testQuickFixInsideLambda(): Unit =
    testQuickFix("Option(thread).foreach(t => t.run())", "Option(thread).foreach(t => t.start())", hint)

  def testQuickFixInsideLambdaWithoutParens(): Unit =
    testQuickFix("Option(thread).foreach(t => t.run)", "Option(thread).foreach(t => t.start())", hint)

  def testQuickFixInsideLambdaWithoutDot(): Unit =
    testQuickFix("Option(thread).foreach(t => t run())", "Option(thread).foreach(t => t.start())", hint)

  def testQuickFixInsideLambdaWithoutDotAndParens(): Unit =
    testQuickFix("Option(thread).foreach(t => t run)", "Option(thread).foreach(t => t.start())", hint)

  // TODO: implement highlighting and quick-fix
  //  def testQuickfixUnderscoreCall(): Unit =
  //    testQuickFix(
  //      "Option(thread).foreach(_.run())",
  //      "Option(thread).foreach(_.start())",
  //      hint
  //    )

  override protected def createTestText(text: String) =
    s"""import scala.language.postfixOps
       |object ThreadRunTest {
       |  val runnable = new Runnable {
       |    override def run(): Unit = {}
       |  }
       |  val thread = new Thread(runnable)
       |  $text
       |}""".stripMargin
}
