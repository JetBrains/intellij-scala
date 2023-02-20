package member

trait Identifiers {
  def `def`: Int = ???

  def `foo bar`: Int = ???

  val `val`: Int = ???

  var `var`: Int = ???

  type `type` = Int

  def _foo: Int = ???

  def & : Int = ???

  def foo_ : Int = ???

  def foo_& : Int = ???

  def `foo &`: Int = ???

  def &(x: Int): Int = ???

  def &(x: Int)(y: Int): Int = ???

  def &&[A]: Int = ???

  def &&[A](x: Int): Int = ???

  val &&& : Int = ???

  var &&&& : Int = ???

  type & = Int

  def contextBound[& : Ordering]: Unit = ???
}