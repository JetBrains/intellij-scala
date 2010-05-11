package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.{PsiElementMock => Psi}
/**
 * Pavel.Fatin, 11.05.2010
 */


class PrevSiblingsIteratorTest extends IteratorTestCase {
  def testEmpty = {
    assertIterates("", Psi("0", Psi("1.1")).getLastChild)
  }

  def testOneSibling = {
    assertIterates("1.1", Psi("0", Psi("1.1"), Psi("1.2")).getLastChild)
  }
  
  def testTwoSiblings = {
    assertIterates("1.2, 1.1", Psi("0", Psi("1.1"), Psi("1.2"), Psi("1.3")).getLastChild)
  }
  
  def testThreeSiblings = {
    assertIterates("1.3, 1.2, 1.1", Psi("0", Psi("1.1"), Psi("1.2"), Psi("1.3"), Psi("1.4")).getLastChild)
  }
  
  def testSubChildren = {
   assertIterates("", Psi("0", Psi("1.1", Psi("2.1"))).getLastChild)
 }

  def iteratorFor(element: PsiElement) = new PrevSiblignsIterator(element)
}