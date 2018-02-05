package org.jetbrains.plugins.scala.failed.resolve

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
  * Created by Anton Yalyshev on 21/04/16.
  */
abstract class FailedResolveCaretTestBase extends SimpleTestCase {

  def doResolveCaretTest(code: String) {
    val (psi, caretPos) = parseText(code, EditorTestUtil.CARET_TAG)
    val reference = psi.findElementAt(caretPos).getParent
    reference match {
      case r: ScReferenceElement => assert(r.resolve() == null, "failed to resolve enclosing object")
      case _ => throw new RuntimeException(failingPassed)
    }
  }

}
