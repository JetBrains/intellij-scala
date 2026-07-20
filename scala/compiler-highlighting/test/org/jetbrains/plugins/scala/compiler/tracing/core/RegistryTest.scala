package org.jetbrains.plugins.scala.compiler.tracing.core

import org.junit.Assert.*
import org.junit.Test

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}

/**
 * Unit tests for the two [[Registry]] implementations in isolation:
 *  - [[QueueRegistry]] — FIFO, several values per key (used to pair keyed begin/end);
 *  - the single-value store from [[Registry.apply]] — one value per key, bounded (used for context
 *    propagation).
 *
 */
class RegistryTest {

  @Test
  def testAddAndGet(): Unit = {
    val registry = Registry[String, String](10)

    registry.add("key1", "span1")
    assertEquals(Some("span1"), registry.peek("key1")) // peek leaves it there

    val retrieved = registry.get("key1")
    assertEquals(Some("span1"), retrieved)
    assertEquals(None, registry.get("key1")) // get is destructive, should be gone now
  }

  @Test
  def testBoundedCapacityEvictsOldest(): Unit = {
    val maxCap = 3
    val registry = Registry[String, Int](maxCap)

    registry.add("k1", 1)
    registry.add("k2", 2)
    registry.add("k3", 3)

    // Cache is full. Adding a 4th should evict the oldest ("k1")
    registry.add("k4", 4)

    assertEquals(None, registry.peek("k1")) // k1 should be evicted
    assertEquals(Some(2), registry.peek("k2"))
    assertEquals(Some(3), registry.peek("k3"))
    assertEquals(Some(4), registry.peek("k4"))
  }

  @Test
  def testCarry(): Unit = {
    val registry = Registry[String, String](5)

    registry.add("oldKey", "mySpan")
    registry.carry("oldKey", "newKey")

    assertEquals(None, registry.peek("oldKey")) // Should be destructively removed
    assertEquals(Some("mySpan"), registry.peek("newKey")) // Should be moved to newKey
  }

  /**
   * Churning entries in and out must not make the registry evict things it should have kept: `get` frees a
   * slot, so the entries added afterwards are well within the bound and nothing may be dropped.
   *
   */
  @Test
  def testChurnDoesNotCausePrematureEviction(): Unit = {
    val registry = Registry[String, String](2)

    for (i <- 1 to 100) {
      registry.add(s"key$i", s"val$i")
      registry.get(s"key$i")
    }

    registry.add("keep1", "valA")
    registry.add("keep2", "valB")

    assertEquals(Some("valA"), registry.peek("keep1"))
    assertEquals(Some("valB"), registry.peek("keep2"))
  }

  /**
   * A destructive `get` must leave no ordering state behind for the key it removed, so re-adding that key
   * makes it the '''youngest''' entry.
   *
   */
  @Test
  def testReAddingAConsumedKeyIsTheYoungestEntryNotTheOldest(): Unit = {
    val registry = Registry[String, Int](2)

    registry.add("x", 1)
    registry.get("x") // consumed

    registry.add("y", 2)
    registry.add("z", 3)
    registry.add("x", 4) // re-added as the youngest entry; capacity is 2, so exactly one must go

    assertEquals("the youngest entry must survive", Some(4), registry.peek("x"))
    assertEquals("the oldest live entry must be the one evicted", None, registry.peek("y"))
    assertEquals(Some(3), registry.peek("z"))
  }

  /**
   * Overwriting a key updates its value but does '''not''' refresh its age: this is a FIFO bound,
   * "a" is written twice yet is still the first to be evicted.
   */
  @Test
  def testOverwritingAKeyDoesNotRefreshItsAge(): Unit = {
    val registry = Registry[String, Int](2)

    registry.add("a", 1)
    registry.add("b", 2)
    registry.add("a", 3)
    assertEquals(Some(3), registry.peek("a")) // value updated

    registry.add("c", 4)

    assertEquals("re-adding must not make 'a' younger than 'b'", None, registry.peek("a"))
    assertEquals(Some(2), registry.peek("b"))
    assertEquals(Some(4), registry.peek("c"))
  }

  /**
   * Concurrent inserts must evict '''exactly''' down to the bound, never past it. Deciding to evict from a
   * separately-read size lets two threads both observe the map as over-capacity and both drop an entry,
   * losing more than they should; consulting the bound from inside `put` makes the two steps one.
   */
  @Test
  def testConcurrentAddsEvictExactlyDownToTheCapacity(): Unit = {
    val maxCap = 100
    val registry = Registry[Int, Int](maxCap)
    val threadCount = 10
    val tasksPerThread = 1000

    val executor = Executors.newFixedThreadPool(threadCount)
    val latch = new CountDownLatch(threadCount)

    // Blast the registry with concurrent additions of distinct keys
    for (i <- 0 until threadCount) {
      executor.submit(new Runnable {
        override def run(): Unit = {
          for (j <- 0 until tasksPerThread) {
            registry.add(i * tasksPerThread + j, j)
          }
          latch.countDown()
        }
      })
    }

    assertTrue("writers must finish", latch.await(20, TimeUnit.SECONDS))
    executor.shutdown()

    // Drain the registry: exactly `maxCap` keys must be left, not fewer.
    var size = 0
    for (i <- 0 until (threadCount * tasksPerThread)) {
      if (registry.get(i).isDefined) size += 1
    }

    assertEquals(s"Registry size should strictly respect the max capacity of $maxCap", maxCap, size)
  }

  /**
   * The observation has to be a single locked call: two `peek`s could straddle a legitimate move and
   * report a false positive, so it goes through `toString`, which renders the whole value view at once.
   */
  @Test
  def testCarryIsAtomicForConcurrentReaders(): Unit = {
    val registry = Registry[String, String](8)
    registry.add("a", "theValue")

    val stop = new AtomicBoolean(false)
    val vanished = new AtomicInteger(0)

    val reader = new Thread(() => {
      while (!stop.get()) {
        if (!registry.toString.contains("theValue")) vanished.incrementAndGet()
      }
    })
    reader.start()

    for (_ <- 1 to 20000) {
      registry.carry("a", "b")
      registry.carry("b", "a")
    }
    stop.set(true)
    reader.join(TimeUnit.SECONDS.toMillis(50))

    assertEquals("carry must move the value within one critical section", 0, vanished.get())
    assertEquals(Some("theValue"), registry.peek("a"))
  }

  /**
   * Rendering the registry iterates a live view of its values, which a `synchronizedMap` only allows
   * inside its own monitor so without that lock a concurrent mutation makes it throw
   * `ConcurrentModificationException`.
   */
  @Test
  def testToStringIsSafeWhileTheRegistryIsMutatedConcurrently(): Unit = {
    val registry = Registry[Int, String](1024)

    // Entries for `toString` to iterate over.
    for (key <- 1 to 100) registry.add(key, "seeded")

    val stop = new AtomicBoolean(false)
    val started = new CountDownLatch(1)

    // Insert and remove one dedicated key over and over. Both are *structural* changes to the map, which
    // is what invalidates a live iterator; the capacity is far above 101 entries so nothing is evicted.
    val writer = new Thread(() => {
      started.countDown()
      while (!stop.get()) {
        registry.add(0, "churn")
        registry.get(0)
      }
    })
    writer.start()
    assertTrue(started.await(30, TimeUnit.SECONDS))

    try {
      // Any ConcurrentModificationException propagates from here and fails the test.
      for (_ <- 1 to 50000) registry.toString
    } finally {
      stop.set(true)
      writer.join(TimeUnit.SECONDS.toMillis(30))
    }
  }
}
