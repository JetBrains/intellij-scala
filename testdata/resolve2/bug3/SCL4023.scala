object SCL4023 {
  trait Y{
    def a: Int
  }

  trait X{ this : Y =>
    def a : Int
    val y = /* line: 7 */a.toByte //a gets highlighted incorrectly
  }
}