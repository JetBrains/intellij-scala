//error: List[Int] vs List[String
trait Foo[X]
val anyOfAbove: Foo[_ >: String with List[Int]] = 1 match {
  case 0 => new Foo[String] {}
  case 1 => new Foo[List[String]] {}
}
//false