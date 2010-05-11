package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.{PsiElementMock => Psi}
/**
 * Pavel.Fatin, 11.05.2010
 */


class NextSiblingsIteratorTest extends IteratorTestCase {
  def testEmpty = {
    assertIterates("", Psi("0", Psi("1.1")).getFirstChild)
  }

  def testOneSibling = {
    assertIterates("1.2", Psi("0", Psi("1.1"), Psi("1.2")).getFirstChild)
  }
  
  def testTwoSiblings = {
    assertIterates("1.2, 1.3", Psi("0", Psi("1.1"), Psi("1.2"), Psi("1.3")).getFirstChild)
  }
  
  def testThreeSiblings = {
    assertIterates("1.2, 1.3, 1.4", Psi("0", Psi("1.1"), Psi("1.2"), Psi("1.3"), Psi("1.4")).getFirstChild)
  }
  
  def testSubChildren = {
   assertIterates("", Psi("0", Psi("1.1", Psi("2.1"))).getFirstChild)
 }

  def iteratorFor(element: PsiElement) = new NextSiblignsIterator(element)
}