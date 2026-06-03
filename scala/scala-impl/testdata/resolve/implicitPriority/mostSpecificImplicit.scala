object T1 {
  def foo = "missed!"
}

object T2 {
  def foo = "bulls eye!"
}

object Conversions {
  implicit def anyToTarget(a: Any): T1.type = T1
  implicit def intToTarget(a: Int): T2.type = T2
}

import Conversions._
val a: Int = 1
a.<ref>foo
