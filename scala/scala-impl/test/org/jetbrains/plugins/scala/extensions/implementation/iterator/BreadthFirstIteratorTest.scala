package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */


class BreadthFirstIteratorTest extends TreeIteratorTestBase {
  def testLongReturn() = {
    assertIterates("0, 1.1, 1.2, 2.1, 3.1", "0 (1.1 (2.1 (3.1)), 1.2)")
  }
  
  def testComplex() = {
    assertIterates("0, 1.1, 1.2, 2.1, 2.2, 2.3", "0 (1.1 (2.1, 2.2), 1.2 (2.3))")
  }
  
  def testPredicate() = {
    val element = parse("0 (1.1 (2.1, 2.2), 1.2 (2.3, 2.4), 1.3 (2.5, 2.6))")
    assertIterates("0, 1.1, 1.2, 1.3, 2.1, 2.2, 2.5, 2.6", createIterator(element, _.toString != "1.2"))
  }
  
  def createIterator(element: PsiElement, predicate: PsiElement => Boolean) = 
    new BreadthFirstIterator(element, predicate)
}