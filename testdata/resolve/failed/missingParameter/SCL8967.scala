object SCL8967 {
  implicit class OptApply(val value: Option[_]) extends AnyVal {
    def apply[T](code: T => Unit): Unit = {}
  }

  class SX {def test(): Unit = {}}

  val sxOp = Some(new SX)
  sxOp.apply[SX] { sx => sx.test() } //OK
  sxOp[SX] { sx => sx.<ref>test() } //!!! "sx" is red, "test()" is red
}
