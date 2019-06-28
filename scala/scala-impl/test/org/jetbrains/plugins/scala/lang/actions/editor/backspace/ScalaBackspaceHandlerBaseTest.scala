package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.junit.runner.RunWith
import org.junit.runners.AllTests

//marker trait for better tests discoverability of the original class
@RunWith(classOf[AllTests])
trait ScalaBackspaceHandlerTestLike

abstract class ScalaBackspaceHandlerBaseTest extends EditorActionTestBase with ScalaBackspaceHandlerTestLike {

  protected def doTest(before: String, after: String): Unit = {
    checkGeneratedTextAfterBackspace(before, after)
  }

}
