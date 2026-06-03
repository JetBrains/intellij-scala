package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

class PrevSiblingsIteratorTest extends IteratorTestCase {
  def testEmpty(): Unit = {
    assertIterates("", parse("0 (1.1)").getLastChild)
  }

  def testOneSibling(): Unit = {
    assertIterates("1.1", parse("0 (1.1, 1.2)").getLastChild)
  }
  
  def testTwoSiblings(): Unit = {
    assertIterates("1.2, 1.1", parse("0 (1.1, 1.2, 1.3)").getLastChild)
  }
  
  def testThreeSiblings(): Unit = {
    assertIterates("1.3, 1.2, 1.1", parse("0 (1.1, 1.2, 1.3, 1.4)").getLastChild)
  }
  
  def testSubChildren(): Unit = {
   assertIterates("", parse("0 (1.1 (2.1))").getLastChild)
 }

  override def createIterator(element: PsiElement) = new PrevSiblignsIterator(element)
}