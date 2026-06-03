object Tag {
  type Tagged[U] = { type Tag = U }
  type @@[T, U] = T with Tagged[U]
  @inline def apply[A, T](a: A): A @@ T = a.asInstanceOf[A @@ T]
}

object Test {
  import Tag._

  sealed trait _MyId
  type MyId = Long @@ _MyId

  val s: Tagged[_MyId] = sys.exit()



  val x: MyId = Tag[Long, _MyId](1)
  def foo(x: MyId) = 1
  def foo(z: String) = 2

  /*line: 18*/foo(Tag[Long, _MyId](1))
}