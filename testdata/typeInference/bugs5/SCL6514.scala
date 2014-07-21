class SCL6514 {
  implicit def foo(implicit i: Int = 123): String = "text"

  implicit val l: List[Int] = List.empty

  implicit def goo[T](implicit s: String, l: List[T]): A[T] = A(l)
  
  case class A[T](t: List[T])

  def m[T](implicit a: A[T]) = a.t

  /*start*/m/*end*/
}
//List[Int]