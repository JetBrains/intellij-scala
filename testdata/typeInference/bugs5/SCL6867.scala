object SCL6867 {
  implicit def foo[T](i: Int)(implicit r: List[T]): Array[T] = sys.exit()

  implicit val l: List[String] = List.empty

  def goo[T](a: Array[T]) = a

  /*start*/goo(123)/*end*/
}
//Array[String]