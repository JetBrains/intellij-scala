package org.jetbrains.plugins.scala.util

import junit.framework.TestCase
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

import scala.collection.compat.immutable.ArraySeq
import scala.util.Random

class BitMaskTest extends TestCase with AssertionMatchers {

  //noinspection TypeAnnotation
  object ExampleMask extends BitMaskStorage {
    val b1 = bool("b1")
    val n1 = nat(max = 5, "n1")
    val b2 = bool("b2")
    val i1 = int(min = -4, max = 22, "i1")
    val l1 = jEnum[ScalaLanguageLevel]("l1")

    override val version: Int = finishAndMakeVersion()
  }

  def testExampleMask(): Unit = {
    import ExampleMask._

    b1.pos shouldBe 0
    b1.chunkSize shouldBe 1

    n1.pos shouldBe 1
    n1.chunkSize shouldBe 3

    b2.pos shouldBe 4
    b2.chunkSize shouldBe 1

    i1.pos shouldBe 5
    i1.chunkSize shouldBe 5
    i1.shiftedMax shouldBe 26

    l1.pos shouldBe 10
  }

  def testRandom(): Unit = {
    val rand = new Random(123)

    def makeRandomMaskStorage(): BitMaskStorage = new BitMaskStorage {
      for (i <- 0 to rand.nextInt(8)) {
        try rand.nextInt(3) match {
          case 0 => bool(s"b$i")
          case 1 => nat(max = math.abs(rand.nextInt()) min 1, s"n$i")
          case 2 =>
            val Seq(a, b) = Seq(rand.nextInt(), rand.nextInt()).sorted
            int(a min b, a max b, s"i$i")
          case _ => ???
        }
        catch {
          case a: AssertionError if a.getMessage.contains("Do not have space") =>
            // cheap hack so we don't have to test if there is still space left
        }
      }

      override val version: Int = finishAndMakeVersion()
    }

    def makeRandomValueFor(mask: BitMask): mask.T = {
      val v = mask match {
        case BitMask.Bool(_) => rand.nextBoolean()
        case BitMask.Nat(_, max) => rand.nextInt(max + 1)
        case BitMask.Integer(_, min, max) => rand.between(min, max + 1)
        case _ => ???
      }
      v.asInstanceOf[mask.T]
    }

    for (_ <- 0 to 10000) {
      val storage = makeRandomMaskStorage()
      val masks = storage.members.values.to(ArraySeq)
      var current = 0

      for (_ <- 0 to 1000) {
        val oldValues = masks.map(_.read(current))

        val i = rand.nextInt(masks.length)
        val mask = masks(i)
        val newValue = makeRandomValueFor(mask)

        val newCurrent = mask.write(current, newValue)

        // test if write was correct
        mask.read(newCurrent) shouldBe newValue

        // test if other values are still the same
        val newValues = masks.map(_.read(newCurrent))
        newValues.patch(i, Nil, 1) shouldBe oldValues.patch(i, Nil, 1)

        current = newCurrent
      }
    }
  }
}
