object Example {
  class A[T]
  class B extends A[B]
  def goo[T <: A[T]](y: T): T = y
  def goo[T <: A[T]](y: T*) = 2

  /* line: 4 */goo(new B)
}