object Low {
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
val aba: Int = 1
aba.<ref>bullseye
