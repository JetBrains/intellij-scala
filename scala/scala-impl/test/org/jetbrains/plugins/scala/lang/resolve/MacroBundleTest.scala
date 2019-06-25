package org.jetbrains.plugins.scala
package lang
package resolve

import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.junit.Assert

/**
  * Nikolay.Tropin
  * 23-Mar-18
  */
class MacroBundleTest extends ScalaResolveTestCase {
  override protected def isIncludeReflectLibrary = true

  override def folderPath = super.folderPath + "resolve/macroBundle/"

  def testSCL8414a(): Unit = findReferenceAtCaret() match {
    case ResolvesTo(_: ScClass) =>
    case _ =>
      Assert.fail("Resolve to macro bundle class expected")
  }

  def testSCL8414b(): Unit = findReferenceAtCaret() match {
    case ResolvesTo(fun: ScFunction) =>
    case _ =>
      Assert.fail("Resolve to a function in macro bundle class expected")
  }
}
