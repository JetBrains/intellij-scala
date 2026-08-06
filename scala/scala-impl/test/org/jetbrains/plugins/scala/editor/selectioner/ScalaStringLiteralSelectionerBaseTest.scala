package org.jetbrains.plugins.scala.editor.selectioner

//noinspection RedundantBlock
abstract class ScalaStringLiteralSelectionerBaseTest extends ExtendWordSelectionHandlerTestBase {

  protected val q   = "\""
  protected val qq  = "\"\""
  protected val qqq = "\"\"\""

  protected def doTestForMultilineAndSingleLine(editorTextStates: Seq[String]): Unit ={
    doTest(editorTextStates)
    val editorTextStatesWithSingleLine = editorTextStates.map(_.replace(qqq, q))
    doTest(editorTextStatesWithSingleLine)
  }
}
