package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement
import junit.framework.{TestCase, Assert}
import org.jetbrains.plugins.scala.lang.psi.PsiElementMock

/**
 * Pavel.Fatin, 11.05.2010
 */

abstract class IteratorTestCase extends TestCase {
  protected def assertIterates(expectation: String, expression: String) { 
    assertIterates(expectation, PsiElementMock.parse(expression))
  }
    
  protected def assertIterates(expectation: String, element: PsiElement) {
    val it = createIterator(element)
    Assert.assertEquals(expectation, it.mkString(", "))
  }
  
  protected def parse(s: String) = PsiElementMock.parse(s)
  
  protected def createIterator(element: PsiElement): Iterator[PsiElement]
}