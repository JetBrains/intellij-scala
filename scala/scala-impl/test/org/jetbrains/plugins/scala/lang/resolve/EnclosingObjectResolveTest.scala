package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

/**
  * @author mucianm 
  * @since 28.03.16.
  */
class EnclosingObjectResolveTest extends SimpleTestCase {

  def testSCL10015(): Unit = {
    val code =
      """
        |object FooModule {
        |  trait Foo
        |  trait Provider {
        |    def foo: Foo
        |  }
        |  object ProductionImpl {
        |    val foo = null // we create and store an instance of foo here
        |    trait Provider extends FooModule.Provider {
        |      override val foo: Foo = <caret>ProductionImpl.this.foo; // <---- ProductionImpl is marked in red!
        |    }
        |  }
        |}
      """.stripMargin
    val (psi, caretPos) = parseText(code, EditorTestUtil.CARET_TAG)
    val reference = psi.findElementAt(caretPos).getParent
    reference match {
      case r: ScReference => assert(!shouldPass ^ r.resolve() != null, "failed to resolve enclosing object")
      case _ => assert(false)
    }
  }
}
