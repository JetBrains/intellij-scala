package org.jetbrains.plugins.scala
package annotator
package usageTracker

import junit.framework.TestCase
import org.junit.Assert.assertEquals

/**
 * Nikolay.Tropin
 * 29-May-18
 */
class ScalaRefCountHolderMapTest extends TestCase {

  def testMap(): Unit = {
    val map = createTestMap(5)

    val key1 = Key(1)
    val key2 = Key(2)
    val key3 = Key(3)
    val key4 = Key(4)
    val key5 = Key(5)

    map.getOrCreate(key1, "old1")
    map.getOrCreate(key2, "old2")
    map.getOrCreate(key3, "old3")
    map.getOrCreate(key4, "old4")
    map.getOrCreate(key5, "old5")

    val resultBefore1 = map.getOrCreate(key1, "new1")
    val resultBefore2 = map.getOrCreate(key2, "new2")
    val resultBefore3 = map.getOrCreate(key3, "new3")
    val resultBefore4 = map.getOrCreate(key4, "new4")
    val resultBefore5 = map.getOrCreate(key5, "new5")

    //old values are still in the map
    assertEquals("old1", resultBefore1)
    assertEquals("old2", resultBefore2)
    assertEquals("old3", resultBefore3)
    assertEquals("old4", resultBefore4)
    assertEquals("old5", resultBefore5)

    Thread.sleep(200)
    map.removeStaleEntries()

    val resultAfter1 = map.getOrCreate(key1, "new1")
    val resultAfter2 = map.getOrCreate(key2, "new2")
    val resultAfter3 = map.getOrCreate(key3, "new3")
    val resultAfter4 = map.getOrCreate(key4, "new4")
    val resultAfter5 = map.getOrCreate(key5, "new5")

    //most recent keys of the first batch remain in the map
    assertEquals("old4", resultAfter4)
    assertEquals("old5", resultAfter5)

    //other keys are removed and replaced
    assertEquals("new1", resultAfter1)
    assertEquals("new2", resultAfter2)
    assertEquals("new3", resultAfter3)
  }

  def testMap2(): Unit = {
    val map = createTestMap(4)

    val key1 = Key(1)
    val key2 = Key(2)
    val key3 = Key(3)
    val key4 = Key(4)
    val key5 = Key(5)

    map.getOrCreate(key1, "old1")
    map.getOrCreate(key2, "old2")
    map.getOrCreate(key3, "old3")
    map.getOrCreate(key4, "old4")
    map.getOrCreate(key5, "old5")

    //4 last values are still there
    val result2 = map.getOrCreate(key2, "new2")
    val result3 = map.getOrCreate(key3, "new3")
    val result4 = map.getOrCreate(key4, "new4")
    val result5 = map.getOrCreate(key5, "new5")

    assertEquals("old2", result2)
    assertEquals("old3", result3)
    assertEquals("old4", result4)
    assertEquals("old5", result5)

    //first result was removed
    val result1 = map.getOrCreate(key1, "new1")
    assertEquals("new1", result1)
  }

  private case class Key(x: Int)

  private def createTestMap(maximumSize: Int) =
    new ScalaRefCountHolderService.TimestampedValueMap[Key, String](
      2,
      maximumSize,
      100
    )
}