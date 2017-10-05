object SCL9888 {

  trait Foo[X] {
    def x: X
  }

  trait Bar extends Foo[Int]

  def gadt[X](f: Foo[X]): Int = f match {
    case f: Bar => /*start*/f.x/*end*/ + 1
  }
}

//Int