package pack1.pack2.pack3

import pack1.pack2.pack3.S1.TestObj.TestString1

class S1 {
  def test() = println(TestString1)
}

object S1 {
  object TestObj {
    val TestString1 = "..."
  }
}
