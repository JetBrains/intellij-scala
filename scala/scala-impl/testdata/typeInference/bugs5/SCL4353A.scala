object SCL4353A {
  val foo : Int => Double = null
  val seq: Seq[Int] = null
  val s : Double => String = null
  val x: Map[String, Seq[Int]] = /*start*/seq.groupBy(foo.andThen(s))/*end*/
}
//Map[String, Seq[Int]]