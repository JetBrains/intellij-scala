package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement
import junit.framework.{TestCase, Assert}

/**
 * Pavel.Fatin, 11.05.2010
 */

abstract class IteratorTestCase extends TestCase {
  def assertIterates(expectation: String, element: PsiElement) {
    val it = iteratorFor(element)
    Assert.assertEquals(expectation, it.mkString(", "))
  }
  
  def iteratorFor(element: PsiElement): Iterator[PsiElement]
}