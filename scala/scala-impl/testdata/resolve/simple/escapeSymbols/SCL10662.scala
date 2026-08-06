class mc1 {
  protected val s: String = ???
}

class mc2 extends mc1{
  val v = 0

  def foo (t: String) = ???

  override protected val s = foo (
    raw"""
       |something
       |"$v/foo/*"
       """.<ref>stripMargin)
}