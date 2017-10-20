object Low {
  def low = "low"
  def bullseye = "missed!"
}

object High {
  def bullseye = "bulls eye!"
}

trait LowPriority {
  implicit def intToLow(a: Int): Low.type = Low
}

object HighPriority extends LowPriority {
  implicit def intToHigh(a: Int): High.type = High
}

import HighPriority._
val a: Int = 1
a.<ref>low
