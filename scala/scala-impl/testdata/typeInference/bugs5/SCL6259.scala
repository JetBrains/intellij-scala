object SCL6259 {
  class G[T]
  def foo[T](x: T): Int = 123
  def foo[T](implicit x: G[T]): String = "text"
  val g: G[String] = null
  /*start*/foo(g)/*end*/
}
//String