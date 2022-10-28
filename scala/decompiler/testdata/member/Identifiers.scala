package member

trait Identifiers {
  def `def`: Int = ???

  val `val`: Int = ???

  var `var`: Int = ???

  type `type` = Int

  def & : Int = ???

  def &(x: Long): Int = ???

  val && : Int = ???

  var &&& : Int = ???

  type & = Int

  def contextBound[& : Ordering]: Unit = ???
}