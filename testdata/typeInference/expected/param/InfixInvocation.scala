object Main {
  def foo(z: Int, u: String => String) = 56
}
Main foo (u = /*start*/_.length.toString/*end*/, z = 54)
//(String) => String