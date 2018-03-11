class Test[T <: Singleton](val x: T) {
  val e = new Test(1)
  val f: 1 = e.x
}