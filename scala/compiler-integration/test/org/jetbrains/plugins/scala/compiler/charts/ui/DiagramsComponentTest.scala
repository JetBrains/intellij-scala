package org.jetbrains.plugins.scala.compiler.charts.ui

import junit.framework.TestCase
import org.junit.Assert.assertEquals

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class DiagramsComponentTest extends TestCase {

  def testStringifyForSegmentTooltip(): Unit = {
    def doTest(duration: FiniteDuration, expectedText: String): Unit = {
      assertEquals(expectedText, DiagramsComponent.stringifyForSegmentTooltip(duration))
    }

    doTest(10.hours, "10 h")
    doTest(2.hours, "2 h")
    doTest(1.hours, "1 h")

    doTest(59.minutes, "59 m")
    doTest(2.minutes, "2 m")
    doTest(1.minutes, "1 m")

    doTest(59.seconds, "59 s")
    doTest(2.seconds, "2 s")
    doTest(1.seconds, "1 s")

    doTest(999.millis, "999 ms")
    doTest(99.millis, "99 ms")
    doTest(2.millis, "2 ms")
    doTest(1.millis, "1 ms")

    // mixed units

    doTest(1.hours + 1.minutes + 1.seconds + 1.millis, "1 h 1 m 1 s")
    doTest(1.hours + 1.minutes + 1.seconds + 99.millis, "1 h 1 m 1 s")
    doTest(1.hours + 1.minutes + 1.seconds + 199.millis, "1 h 1 m 1 s")
    doTest(1.hours + 1.minutes + 1.seconds + 499.millis, "1 h 1 m 1 s")
    doTest(1.hours + 1.minutes + 1.seconds + 501.millis, "1 h 1 m 2 s")
    doTest(1.hours + 1.minutes + 1.seconds + 999.millis, "1 h 1 m 2 s")

    doTest(1.minutes + 1.seconds + 1.millis, "1 m 1 s")
    doTest(1.minutes + 1.seconds + 99.millis, "1 m 1 s")
    doTest(1.minutes + 1.seconds + 199.millis, "1 m 1 s")
    doTest(1.minutes + 1.seconds + 499.millis, "1 m 1 s")
    doTest(1.minutes + 1.seconds + 501.millis, "1 m 2 s")
    doTest(1.minutes + 1.seconds + 999.millis, "1 m 2 s")

    doTest(59.seconds + 1.millis, "59 s")
    doTest(59.seconds + 99.millis, "59.1 s")
    doTest(59.seconds + 199.millis, "59.2 s")
    doTest(59.seconds + 499.millis, "59.5 s")
    doTest(59.seconds + 501.millis, "59.5 s")
    doTest(59.seconds + 999.millis, "1 m")

    doTest(1.seconds + 1.millis, "1 s")
    doTest(1.seconds + 99.millis, "1.1 s")
    doTest(1.seconds + 199.millis, "1.2 s")
    doTest(1.seconds + 499.millis, "1.5 s")
    doTest(1.seconds + 501.millis, "1.5 s")
    doTest(1.seconds + 999.millis, "2 s")

    doTest(0.seconds + 1.millis, "1 ms")
    doTest(0.seconds + 99.millis, "99 ms")
    doTest(0.seconds + 199.millis, "199 ms")
    doTest(0.seconds + 499.millis, "499 ms")
    doTest(0.seconds + 501.millis, "501 ms")
    doTest(0.seconds + 999.millis, "999 ms")
  }

}


