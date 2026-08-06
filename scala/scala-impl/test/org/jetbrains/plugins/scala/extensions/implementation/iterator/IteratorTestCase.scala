package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement
import junit.framework.TestCase
import org.jetbrains.plugins.scala.lang.psi.PsiElementMock
import org.junit.Assert

abstract class IteratorTestCase extends TestCase {
  protected def assertIterates(expectation: String, expression: String): Unit = {
    assertIterates(expectation, PsiElementMock.parse(expression))
  }
  
  protected def assertIterates(expectation: String, element: PsiElement): Unit = {
    assertIterates(expectation, createIterator(element))
  }

  protected def assertIterates(expectation: String, iterator: Iterator[PsiElement]): Unit = {
    Assert.assertEquals(expectation, iterator.mkString(", "))
  }
  
  protected def parse(s: String) = PsiElementMock.parse(s)
  
  protected def createIterator(element: PsiElement): Iterator[PsiElement]
}