package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.{PsiElementMock => Psi}

/**
 * Pavel.Fatin, 11.05.2010
 */


class PrevSiblingsIteratorTest extends IteratorTestCase {
  def testEmpty() = {
    assertIterates("", parse("0 (1.1)").getLastChild)
  }

  def testOneSibling() = {
    assertIterates("1.1", parse("0 (1.1, 1.2)").getLastChild)
  }
  
  def testTwoSiblings() = {
    assertIterates("1.2, 1.1", parse("0 (1.1, 1.2, 1.3)").getLastChild)
  }
  
  def testThreeSiblings() = {
    assertIterates("1.3, 1.2, 1.1", parse("0 (1.1, 1.2, 1.3, 1.4)").getLastChild)
  }
  
  def testSubChildren() = {
   assertIterates("", parse("0 (1.1 (2.1))").getLastChild)
 }

  def createIterator(element: PsiElement) = new PrevSiblignsIterator(element)
}