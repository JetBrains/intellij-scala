object SCL9896 {
  val opt: Option[String] = ???
  val res1 = opt.map(_.<ref>toString _) // Cannot resolve symbol toString
  val res2 = opt.map(s => s.toString _) // this is OK
}
