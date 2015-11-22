package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */


class ChildrenIteratorTest extends IteratorTestCase {
  def testEmpty() = {
    assertIterates("", "0")
  }

  def testOneChild() = {
    assertIterates("1.1", "0 (1.1)")
  }
  
  def testTwoChildren() = {
    assertIterates("1.1, 1.2", "0 (1.1, 1.2)")
  }
  
  def testThreeChildren() = {
    assertIterates("1.1, 1.2, 1.3", "0 (1.1, 1.2, 1.3)")
  }
  
  def testSiblings() = {
    assertIterates("", parse("0 (1.1, 1.2, 1.3)").getFirstChild.getNextSibling)
  }
  
  def testSubChildren() = {
   assertIterates("1.1", "0 (1.1 (2.1))")
 }

  def createIterator(element: PsiElement) = new ChildrenIterator(element)
}