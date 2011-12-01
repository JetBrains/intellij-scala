object SCL3865 {
  object Test {
    import ColorType._, PositionType._

    def foo(s: Set[(ColorType, PositionType)]): Int = 1
    def foo(s: Int): Boolean = false

    /*start*/foo(Set((Blue, Left), (Blue, Right), (Red, Left), (Red, Right)))/*end*/
  }

  object ColorType extends Enumeration {
    type ColorType = Value
    val Blue, Red = Value
  }

  object PositionType extends Enumeration {
    type PositionType = Value
    val Left, Right = Value
  }
}
//Int