package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */


class ContextsIteratorTest extends IteratorTestCase {
  def testEmpty() = {
    assertIterates("", "0")
  }

  def testOneParent() = {
    assertIterates("0", parse("0 (1.1)").getFirstChild)
  }

  def testTwoParents() = {
    assertIterates("1.1, 0", parse("0 (1.1 (2.1))").getFirstChild.getFirstChild)
  }

  def testThreeParents() = {
    assertIterates("2.1, 1.1, 0", parse("0 (1.1 (2.1 (3.1)))").getFirstChild.getFirstChild.getFirstChild)
  }

  def testSiblings() = {
    assertIterates("0", parse("0 (1.1, 1.2, 1.3)").getFirstChild.getNextSibling)
  }

  def testChildren() = {
    assertIterates("", "0 (1.1)")
  }

  def createIterator(element: PsiElement) = new ContextsIterator(element)
}