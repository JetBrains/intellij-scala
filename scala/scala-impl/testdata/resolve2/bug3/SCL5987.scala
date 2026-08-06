object SCL5987 {
  class GenericType[T](t: T) {
    protected def this() = this(null)
  }
  class GenericType2[T] protected ()

  object GenericTypeUser {
    val genericType = new /* line: 3, name: this */GenericType[List[Int]](){}
  }
}