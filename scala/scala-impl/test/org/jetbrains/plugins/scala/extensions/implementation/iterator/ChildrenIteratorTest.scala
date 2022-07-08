package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

class ChildrenIteratorTest extends IteratorTestCase {
  def testEmpty(): Unit = {
    assertIterates("", "0")
  }

  def testOneChild(): Unit = {
    assertIterates("1.1", "0 (1.1)")
  }
  
  def testTwoChildren(): Unit = {
    assertIterates("1.1, 1.2", "0 (1.1, 1.2)")
  }
  
  def testThreeChildren(): Unit = {
    assertIterates("1.1, 1.2, 1.3", "0 (1.1, 1.2, 1.3)")
  }
  
  def testSiblings(): Unit = {
    assertIterates("", parse("0 (1.1, 1.2, 1.3)").getFirstChild.getNextSibling)
  }
  
  def testSubChildren(): Unit = {
   assertIterates("1.1", "0 (1.1 (2.1))")
 }

  override def createIterator(element: PsiElement) = new ChildrenIterator(element)
}