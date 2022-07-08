package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

class ParentsIteratorTest extends IteratorTestCase {
   def testEmpty(): Unit = {
    assertIterates("", "0")
  }

  def testOneParent(): Unit = {
    assertIterates("0", parse("0 (1.1)").getFirstChild)
  }

  def testTwoParents(): Unit = {
    assertIterates("1.1, 0", parse("0 (1.1 (2.1))").getFirstChild.getFirstChild)
  }

  def testThreeParents(): Unit = {
    assertIterates("2.1, 1.1, 0", parse("0 (1.1 (2.1 (3.1)))").getFirstChild.getFirstChild.getFirstChild)
  }

  def testSiblings(): Unit = {
    assertIterates("0", parse("0 (1.1, 1.2, 1.3)").getFirstChild.getNextSibling)
  }

  def testChildren(): Unit = {
    assertIterates("", "0 (1.1)")
  }

  override def createIterator(element: PsiElement) = new ParentsIterator(element)
}