package org.jetbrains.plugins.scala.refCountHolder

import junit.framework.{Assert, TestCase}
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder

import scala.concurrent.duration.{Duration, DurationInt}

/**
  * Nikolay.Tropin
  * 29-May-18
  */
class ScalaRefCountHolderMapTest extends TestCase {

  def testMap(): Unit = {
    val map = createTestMap(2, 100.millis)

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
    Assert.assertEquals("old1", resultBefore1)
    Assert.assertEquals("old2", resultBefore2)
    Assert.assertEquals("old3", resultBefore3)
    Assert.assertEquals("old4", resultBefore4)
    Assert.assertEquals("old5", resultBefore5)

    Thread.sleep(200)
    map.removeStaleEntries()

    val resultAfter1 = map.getOrCreate(key1, "new1")
    val resultAfter2 = map.getOrCreate(key2, "new2")
    val resultAfter3 = map.getOrCreate(key3, "new3")
    val resultAfter4 = map.getOrCreate(key4, "new4")
    val resultAfter5 = map.getOrCreate(key5, "new5")

    //most recent keys of the first batch remain in the map
    Assert.assertEquals("old4", resultAfter4)
    Assert.assertEquals("old5", resultAfter5)

    //other keys are removed and replaced
    Assert.assertEquals("new1", resultAfter1)
    Assert.assertEquals("new2", resultAfter2)
    Assert.assertEquals("new3", resultAfter3)
  }

  private def createTestMap(minSize: Int, storageTime: Duration) =
    new ScalaRefCountHolder.WeakKeyTimestampedValueMap[Key, String](minSize, storageTime)

  private case class Key(x: Int)
}