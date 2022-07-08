package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.junit.Assert._

class ResolveClassTest extends ScalaResolveTestCase {
  override def folderPath = super.folderPath + "resolve/class/companion/"

  def testCaseClass(): Unit = {
    val ref = findReferenceAtCaret()
    val resolved = ref.resolve
    assertNotNull(resolved)
    assertTrue(resolved.isInstanceOf[ScObject])
  }

  def testApplyToCase(): Unit = {
    val ref = findReferenceAtCaret()
    val resolved = ref.resolve
    assertNotNull(resolved)
  }

  def testApplyToObjectApply(): Unit = {
    val ref = findReferenceAtCaret()
    val resolved = ref.resolve
    assertNotNull(resolved)
    assertTrue(resolved.isInstanceOf[ScFunction])
  }

  def testApplyFromTrait(): Unit = {
    val ref = findReferenceAtCaret()
    val resolved = ref.resolve
    assertTrue(resolved match {
      case f: ScFunction if !f.isSynthetic => true
    })
  }
}