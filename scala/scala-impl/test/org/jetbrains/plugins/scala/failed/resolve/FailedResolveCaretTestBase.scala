package org.jetbrains.plugins.scala
package failed.resolve

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class FailedResolveCaretTestBase extends SimpleTestCase {

  def doResolveCaretTest(code: String): Unit = {
    val (psi, caretPos) = parseScalaFileAndGetCaretPosition(code, EditorTestUtil.CARET_TAG)
    val reference = psi.findElementAt(caretPos).getParent
    reference match {
      case r: ScReference => assert(r.resolve() == null, "failed to resolve enclosing object")
      case _ => throw new RuntimeException(failingPassed)
    }
  }

}
