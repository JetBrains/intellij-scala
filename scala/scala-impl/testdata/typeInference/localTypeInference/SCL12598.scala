object Test {
  trait Abc[T]

  implicit def abcLong: Abc[Long] = ???
  implicit def abcInt:  Abc[Int] = ???

  class Clazz[U](arg: Abc[U])(implicit abc: Abc[U])

  /*start*/new Clazz(abcLong)/*end*/
}
//Test.Clazz[Long]