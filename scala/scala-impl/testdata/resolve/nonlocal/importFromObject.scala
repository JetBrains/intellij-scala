trait Tupo{
  import b._
  def foo = I<ref>f(3)
}

object b {
  case class If(i : Int)
}