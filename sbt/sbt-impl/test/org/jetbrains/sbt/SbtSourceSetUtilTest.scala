package org.jetbrains.sbt

import org.jetbrains.sbt.SbtSourceSetUtil.withoutSourceSetSuffix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class SbtSourceSetUtilTest:

  @Test
  def testMainSuffixIsRemoved(): Unit =
    assertEquals("root.C", withoutSourceSetSuffix("root.C.main"))

  @Test
  def testTestSuffixIsRemoved(): Unit =
    assertEquals("root.C", withoutSourceSetSuffix("root.C.test"))

  @Test
  def testNumberedSuffixIsRemoved(): Unit =
    assertEquals("root.C", withoutSourceSetSuffix("root.C.main~1"))

  @Test
  def testSuffixOfADisplayNameIsRemoved(): Unit =
    assertEquals("C", withoutSourceSetSuffix("C.main"))

  @Test
  def testNameWithoutSuffixIsKept(): Unit =
    assertEquals("root.C", withoutSourceSetSuffix("root.C"))

  @Test
  def testOnlyTheLastSegmentIsASuffix(): Unit =
    assertEquals("root.main.C", withoutSourceSetSuffix("root.main.C"))

  @Test
  def testSuffixIsAWholeSegment(): Unit =
    assertEquals("root.domain", withoutSourceSetSuffix("root.domain"))

  @Test
  def testOtherScopesAreNotSuffixes(): Unit =
    assertEquals("root.C.it", withoutSourceSetSuffix("root.C.it"))
