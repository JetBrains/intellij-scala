object Test {
  class C extends T with U
  trait T {
    case class Box[T](value: T)
  }
  trait U { self: C =>
    val box: Box[String] = null
    class Inner {
      box.value.isEmpty // okay

      val Box(value) = box; /*start*/value.isEmpty/*end*/ // good code red
    }
    val Box(value) = box; value.isEmpty // okay
  }
}
//Boolean