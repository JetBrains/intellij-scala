trait Foo[X]
val anyOfAbove: Foo[_ >: String with List[Int]] = 1 match {
  case 0 => new Foo[String] {}
  case 1 => new Foo[List[Int]] {}
}
//true