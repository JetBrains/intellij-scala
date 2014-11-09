package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement
import junit.framework.{Assert, TestCase}
import org.jetbrains.plugins.scala.lang.psi.PsiElementMock

/**
 * Pavel.Fatin, 11.05.2010
 */

abstract class IteratorTestCase extends TestCase {
  protected def assertIterates(expectation: String, expression: String) { 
    assertIterates(expectation, PsiElementMock.parse(expression))
  }
  
  protected def assertIterates(expectation: String, element: PsiElement) {
    assertIterates(expectation, createIterator(element))
  }

  protected def assertIterates(expectation: String, iterator: Iterator[PsiElement]) {
    Assert.assertEquals(expectation, iterator.mkString(", "))
  }
  
  protected def parse(s: String) = PsiElementMock.parse(s)
  
  protected def createIterator(element: PsiElement): Iterator[PsiElement]
}