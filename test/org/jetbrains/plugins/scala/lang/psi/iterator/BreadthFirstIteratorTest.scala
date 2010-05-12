package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */


class BreadthFirstIteratorTest extends TreeIteratorTestBase {
  def testLongReturn = {
    assertIterates("0, 1.1, 1.2, 2.1, 3.1", "0 (1.1 (2.1 (3.1)), 1.2)")
  }
  
  def testComplex = {
    assertIterates("0, 1.1, 1.2, 2.1, 2.2, 2.3", "0 (1.1 (2.1, 2.2), 1.2 (2.3))")
  }

  def createIterator(element: PsiElement) = new BreadthFirstIterator(element)
}