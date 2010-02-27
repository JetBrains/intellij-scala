object Test {
  val a = List(Seq(1, 2, 3) , Seq(2, 3, 4))

  /*start*/a.map(_.map(_ + 1))/*end*/
}
//List[Seq[Int]]