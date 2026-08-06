package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

abstract class TreeIteratorTestBase extends IteratorTestCase {
  def testEmpty(): Unit = {
    assertIterates("0", "0")
  }

  def testSingleChild(): Unit = {
    assertIterates("0, 1.1", "0 (1.1)")
  }

  def testTwoChildren(): Unit = {
    assertIterates("0, 1.1, 1.2", "0 (1.1, 1.2)")
  }

  def testThreeChildren(): Unit = {
    assertIterates("0, 1.1, 1.2, 1.3", "0 (1.1, 1.2, 1.3)")
  }
  
  def testTwoLevels(): Unit = {
    assertIterates("0, 1.1, 2.1", "0 (1.1 (2.1))")
  }
  
  def testThreeLevels(): Unit = {
    assertIterates("0, 1.1, 2.1, 3.1", "0 (1.1 (2.1 (3.1)))")
  }

  def testInitialElementSibling(): Unit = {
    assertIterates("1.1", parse("0 (1.1, 1.2)").getFirstChild)
  }

  def testInitialElementSiblingOnReturn(): Unit = {
    assertIterates("1.1, 2.1", parse("0 (1.1 (2.1), 1.2)").getFirstChild)
  }
  
  def testPredicateOnOriginalElement(): Unit = {
    assertIterates("0", createIterator(parse("0"), _.toString != "0"))
  }
  
  def testPredicateOnFirstChild(): Unit = {
    assertIterates("0, 1.1", createIterator(parse("0 (1.1)"), _.toString != "1.1"))
  }
  
  def testPredicateOnMiddleChild(): Unit = {
    assertIterates("0, 1.1, 1.2, 1.3", createIterator(parse("0 (1.1, 1.2, 1.3)"), _.toString != "1.2"))
  }

  def testPredicateOnNested(): Unit = {
    assertIterates("0, 1.1, 1.2", createIterator(parse("0 (1.1 (1.2 (1.3)))"), _.toString != "1.2"))
  }
  
  final override def createIterator(element: PsiElement) =
    createIterator(element, _ => true)
  
  protected def createIterator(element: PsiElement, predicate: PsiElement => Boolean): Iterator[PsiElement]
}