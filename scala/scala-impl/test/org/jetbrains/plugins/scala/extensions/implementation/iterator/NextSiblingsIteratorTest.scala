package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

class NextSiblingsIteratorTest extends IteratorTestCase {
  def testEmpty(): Unit = {
    assertIterates("", parse("0 (1.1)").getFirstChild)
  }

  def testOneSibling(): Unit = {
    assertIterates("1.2", parse("0 (1.1, 1.2)").getFirstChild)
  }
  
  def testTwoSiblings(): Unit = {
    assertIterates("1.2, 1.3", parse("0 (1.1, 1.2, 1.3)").getFirstChild)
  }
  
  def testThreeSiblings(): Unit = {
    assertIterates("1.2, 1.3, 1.4", parse("0 (1.1, 1.2, 1.3, 1.4)").getFirstChild)
  }
  
  def testSubChildren(): Unit = {
   assertIterates("", parse("0 (1.1 (2.1))").getFirstChild)
 }

  override def createIterator(element: PsiElement) = new NextSiblignsIterator(element)
}