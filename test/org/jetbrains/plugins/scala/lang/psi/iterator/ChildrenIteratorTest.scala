package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.{PsiElementMock => Psi}

/**
 * Pavel.Fatin, 11.05.2010
 */


class ChildrenIteratorTest extends IteratorTestCase {
  def testEmpty = {
    assertIterates("", Psi("0"))
  }

  def testOneChild = {
    assertIterates("1.1", Psi("0", Psi("1.1")))
  }
  
  def testTwoChildren = {
    assertIterates("1.1, 1.2", Psi("0", Psi("1.1"), Psi("1.2")))
  }
  
  def testThreeChildren = {
    assertIterates("1.1, 1.2, 1.3", Psi("0", Psi("1.1"), Psi("1.2"), Psi("1.3")))
  }
  
  def testSiblings = {
    assertIterates("", Psi("0", Psi("1.1"), Psi("1.2"), Psi("1.3")).getFirstChild.getNextSibling)
  }
  
  def testSubChildren = {
   assertIterates("1.1", Psi("0", Psi("1.1", Psi("2.1"))))
 }

  def iteratorFor(element: PsiElement) = new ChildrenIterator(element)
}