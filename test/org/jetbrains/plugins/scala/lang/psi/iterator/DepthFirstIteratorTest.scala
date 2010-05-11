package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.{PsiElementMock => Psi}

/**
 * Pavel.Fatin, 11.05.2010
 */


class DepthFirstIteratorTest extends TreeIteratorTestBase {
  def testLongReturn = {
    val v = Psi("0", Psi("1.1", Psi("2.1", Psi("3.1"))), Psi("1.2"))
    assertIterates("0, 1.1, 2.1, 3.1, 1.2", v)
  }

  def testComplex = {
    val v = Psi("0", Psi("1.1", Psi("2.1"), Psi("2.2")), Psi("1.2", Psi("2.3")))
    assertIterates("0, 1.1, 2.1, 2.2, 1.2, 2.3", v)
  }

  def iteratorFor(element: PsiElement) = new DepthFirstIterator(element)
}