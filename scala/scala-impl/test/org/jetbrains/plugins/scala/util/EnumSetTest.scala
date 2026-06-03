package org.jetbrains.plugins.scala.util

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.EnumSet._
import org.junit.Assert._

class EnumSetTest extends TestCase {
  import JavaEnum._

  def testEnumSet(): Unit = {

    val ab = EnumSet(A, B, B, B, B, A, B)
    val ab2 = EnumSet.empty[JavaEnum] ++ A ++ B

    assertTrue(ab.contains(A) && ab.contains(B) && !ab.contains(C) && !ab.contains(D))
    assertTrue(ab2.contains(A) && ab2.contains(B) && !ab2.contains(C) && !ab2.contains(D))

    assertTrue(ab == ab2)

    val bc = EnumSet(B) ++ EnumSet(C)

    assertTrue(!bc.contains(A) && bc.contains(B) && bc.contains(C) && !bc.contains(D))

    val abc = ab ++ bc

    assertTrue(abc.contains(A) && abc.contains(B) && abc.contains(C) && !abc.contains(D))
  }

  def testEnumSetToArray(): Unit = {
    val acd = EnumSet(D) ++ EnumSet(A) ++ C

    assertTrue(acd.toArray sameElements Array(A, C, D))
  }

  def testEnumSetIsEmpty(): Unit = {
    val empty1 = EnumSet.empty[JavaEnum]
    val empty2 = EnumSet[JavaEnum]()
    val empty3 = EnumSet.readFromInt[JavaEnum](0)

    assertTrue(empty1.isEmpty && empty2.isEmpty && empty3.isEmpty)
    assertTrue(empty1 == empty2 && empty2 == empty3)
  }

}
