package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */


abstract class TreeIteratorTestBase extends IteratorTestCase {
  def testEmpty() = {
    assertIterates("0", "0")
  }

  def testSingleChild() = {
    assertIterates("0, 1.1", "0 (1.1)")
  }

  def testTwoChildren() = {
    assertIterates("0, 1.1, 1.2", "0 (1.1, 1.2)")
  }

  def testThreeChildren() = {
    assertIterates("0, 1.1, 1.2, 1.3", "0 (1.1, 1.2, 1.3)")
  }
  
  def testTwoLevels() = {
    assertIterates("0, 1.1, 2.1", "0 (1.1 (2.1))")
  }
  
  def testThreeLevels() = {
    assertIterates("0, 1.1, 2.1, 3.1", "0 (1.1 (2.1 (3.1)))")
  }

  def testInitialElementSibling() = {
    assertIterates("1.1", parse("0 (1.1, 1.2)").getFirstChild)
  }

  def testInitialElementSiblingOnReturn() = {
    assertIterates("1.1, 2.1", parse("0 (1.1 (2.1), 1.2)").getFirstChild)
  }
  
  def testPredicateOnOriginalElement() = {
    assertIterates("0", createIterator(parse("0"), _.toString != "0"))
  }
  
  def testPredicateOnFirstChild() = {
    assertIterates("0, 1.1", createIterator(parse("0 (1.1)"), _.toString != "1.1"))
  }
  
  def testPredicateOnMiddleChild() = {
    assertIterates("0, 1.1, 1.2, 1.3", createIterator(parse("0 (1.1, 1.2, 1.3)"), _.toString != "1.2"))
  }

  def testPredicateOnNested() = {
    assertIterates("0, 1.1, 1.2", createIterator(parse("0 (1.1 (1.2 (1.3)))"), _.toString != "1.2"))
  }
  
  final override def createIterator(element: PsiElement) =
    createIterator(element, _ => true)
  
  protected def createIterator(element: PsiElement, predicate: PsiElement => Boolean): Iterator[PsiElement]
}