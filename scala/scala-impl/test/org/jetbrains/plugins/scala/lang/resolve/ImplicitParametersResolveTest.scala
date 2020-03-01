package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.junit.Assert._

class ImplicitParametersResolveTest extends ScalaResolveTestCase {

  override def folderPath = super.folderPath + "resolve/implicitParameter"

  def testlocalValAsImplicitParam(): Unit = {
    val ref = findReferenceAtCaret()
    assertTrue(ref.resolve.isInstanceOf[ScFunctionDefinition])
  }

  def testSCL16246(): Unit = {
    findReferenceAtCaret() match {
      case ref: ScReference =>
        assertTrue(ref.multiResolveScala(false).length == 1)
    }
  }

}
