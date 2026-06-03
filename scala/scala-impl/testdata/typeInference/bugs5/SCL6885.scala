class SCL6885 {
  private var bar:String = "bar"

  def bar_=(x: Int, u: Int): Int = 123
  def foo(s:String):Unit = /*start*/bar_=(s)/*end*/
}
//Unit