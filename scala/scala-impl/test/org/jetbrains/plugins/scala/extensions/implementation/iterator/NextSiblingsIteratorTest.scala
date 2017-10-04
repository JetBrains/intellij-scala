package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */


class NextSiblingsIteratorTest extends IteratorTestCase {
  def testEmpty() = {
    assertIterates("", parse("0 (1.1)").getFirstChild)
  }

  def testOneSibling() = {
    assertIterates("1.2", parse("0 (1.1, 1.2)").getFirstChild)
  }
  
  def testTwoSiblings() = {
    assertIterates("1.2, 1.3", parse("0 (1.1, 1.2, 1.3)").getFirstChild)
  }
  
  def testThreeSiblings() = {
    assertIterates("1.2, 1.3, 1.4", parse("0 (1.1, 1.2, 1.3, 1.4)").getFirstChild)
  }
  
  def testSubChildren() = {
   assertIterates("", parse("0 (1.1 (2.1))").getFirstChild)
 }

  def createIterator(element: PsiElement) = new NextSiblignsIterator(element)
}