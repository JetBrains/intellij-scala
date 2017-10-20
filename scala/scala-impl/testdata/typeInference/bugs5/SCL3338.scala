object SCL3338 {
  implicit def convert(p: (String, String)): String = p._1.concat(p._2)
  def foo(p: String) = "text"
  def foo(i: Int) = 123
/*start*/(foo("xxx", "zzz"), foo(("xxx", "zzz")))/*end*/
}
//(String, String)