object SCL6783 {
  class O[T]
  object K {
    implicit val x: O[Int] = new O[Int]
  }

  class Z[T](x: T)(implicit o: O[T])

  object L {
    import K.x

    class K extends Z(123)
  }

  object T {
    import K.x

    class K extends Z[Int](123)
  }
}
/*
object SCL6783 {
  class O[T]
  object K {
    implicit val x: O[Int] = new O[Int]
  }

  class Z[T](x: T)(implicit o: O[T])

  object L {
    import K.x

    class K extends Z(123)
  }

  object T {
    import K.x

    class K extends Z[Int](123)
  }
}
 */