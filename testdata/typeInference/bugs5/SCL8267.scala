object SCL8267 {
  val l: Option[List[Int]] = Some(List(1, 2, 3))

  /*start*/l.map {
    for {
      x <- _
      z = x + 1
    } yield x + 1
  }/*end*/
}
//Option[List[Int]]