class Foo[-T]

object Wrapper {
  val x: (Foo[Foo[T]]) forSome {type T} = ???
  /*start*/x/*end*/
}
//Foo[Foo[Any]]