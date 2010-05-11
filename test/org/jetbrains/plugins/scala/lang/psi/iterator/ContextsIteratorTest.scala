package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.{PsiElementMock => Psi}
/**
 * Pavel.Fatin, 11.05.2010
 */


class ContextsIteratorTest extends IteratorTestCase {
  def testEmpty = {
    assertIterates("", Psi("0"))
  }

  def testOneParent = {
    assertIterates("0", Psi("0", Psi("1.1")).getFirstChild)
  }
  
  def testTwoParents = {
    assertIterates("1.1, 0", Psi("0", Psi("1.1", Psi("1.2"))).getFirstChild.getFirstChild)
  }
  
  def testThreeParents = {
    val psi = Psi("0", Psi("1.1", Psi("1.2", Psi("1.3")))).getFirstChild.getFirstChild.getFirstChild
    assertIterates("1.2, 1.1, 0", psi)
  }
  
  def testSiblings = {
    assertIterates("0", Psi("0", Psi("1.1"), Psi("1.2"), Psi("1.3")).getFirstChild.getNextSibling)
  }
  
  def testChildren = {
   assertIterates("", Psi("0", Psi("1.1")))
 }

  def iteratorFor(element: PsiElement) = new ContextsIterator(element)
}