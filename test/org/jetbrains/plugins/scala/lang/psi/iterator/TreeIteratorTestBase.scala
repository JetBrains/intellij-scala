package org.jetbrains.plugins.scala.lang.psi.iterator

import org.jetbrains.plugins.scala.lang.psi.{PsiElementMock => Psi}

/**
 * Pavel.Fatin, 11.05.2010
 */


abstract class TreeIteratorTestBase extends IteratorTestCase {
  def testEmpty = {
    assertIterates("0", Psi("0"))
  }

  def testSingleChild = {
    assertIterates("0, 1.1", Psi("0", Psi("1.1")))
  }

  def testTwoChildren = {
    assertIterates("0, 1.1, 1.2", Psi("0", Psi("1.1"), Psi("1.2")))
  }

  def testThreeChildren = {
    assertIterates("0, 1.1, 1.2, 1.3", Psi("0", Psi("1.1"), Psi("1.2"), Psi("1.3")))
  }
  
  def testTwoLevels = {
    assertIterates("0, 1.1, 2.1", Psi("0", Psi("1.1", Psi("2.1"))))
  }
  
  def testThreeLevels = {
    assertIterates("0, 1.1, 2.1, 3.1", Psi("0", Psi("1.1", Psi("2.1", Psi("3.1")))))
  }

  def testInitialElementSibling = {
    assertIterates("1.1", Psi("0", Psi("1.1"), Psi("1.2")).getFirstChild)
  }

  def testInitialElementSiblingOnReturn = {
    assertIterates("1.1, 2.1", Psi("0", Psi("1.1", Psi("2.1")), Psi("1.2")).getFirstChild)
  }
}