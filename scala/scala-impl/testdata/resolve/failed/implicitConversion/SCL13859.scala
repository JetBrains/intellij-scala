implicit class F(private val a: Int) extends AnyVal {
  def tplus[T](b: Int): T = (a+b).asInstanceOf[T]
}
def f[A](a: Int): A = a.asInstanceOf[A]
println((1 tplus[Float] 2).<ref>isNaN)