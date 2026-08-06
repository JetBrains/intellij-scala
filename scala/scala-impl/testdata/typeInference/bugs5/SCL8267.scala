object SCL8267 {
  val l: Option[List[Int]] = Some(List(1, 2, 3))

  /*start*/(l.map {
    for {
      x <- _
      z = x + 1
    } yield x + 1
  }, l.map {
    for {
      x <- List(1, 2, 3)
      z <- _
    } yield x + z
  }, l.map {
      1 match {
        case _ if _.length == 1 => 123
      }
    })/*end*/
}
//(Option[List[Int]], Option[List[Int]], Option[Int])