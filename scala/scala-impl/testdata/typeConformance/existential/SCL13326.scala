class P[T]

class Base {
  /*caret*/
  val n: P[x.type] forSome { val x: Double } = new P[Double with Singleton]()
}
//True