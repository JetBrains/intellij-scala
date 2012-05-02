trait Foo[X] // same as LowerBound2.scala, just using type aliases
type A = String
type B = List[Int]
val anyOfAbove: Foo[_ >: A with B] = 1 match {
  case 0 => new Foo[String] {}
  case 1 => new Foo[List[String]] {}
}
//false