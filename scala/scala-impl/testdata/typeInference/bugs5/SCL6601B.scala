object SCL6601B {
  val x: (_, Int) = ???
  val y: _ => Int = ???
  val z: (Int, _) => Int = ???
  val k: (_, Int) => Int = ???
  val h: Int => _ = ???
  val j: (_, Int) => _ = ???

  /*start*/(x, y, z, k, h, j)/*end*/
}
//(Tuple2[_, Int], Function1[_, Int], Function2[Int, _, Int], Function2[_, Int, Int], Function1[Int, _], Function2[_, Int, _])