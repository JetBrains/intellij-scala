package pack1.pack2.pack4

import pack1.pack2.pack4.S1.TestObj.TestString1

class S1 {
  def test() = println(TestString1)
}

object S1 {
  object TestObj {
    val TestString1 = "..."
  }
}
