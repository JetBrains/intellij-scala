object SCL6022 {
  object A {
    def apply[T]: T = sys.exit()

    def apply(x: Int) = 123
  }

  val a = A

  object B {
    /*start*/a[Int]/*end*/
  }
}
//Int