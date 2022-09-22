package member

trait Identifiers {
  def `def`: Int = ???

  val `val`: Int = ???

  var `var`: Int = ???

  type `type` = Int

  extension (i: Int)
    def `def`: Int = ???

  def & : Int = ???

  def &(x: Long): Int = ???

  val && : Int = ???

  var &&& : Int = ???

  type & = Int

  extension (i: Int)
    def & : Int = ???

  def contextBound[& : Ordering]: Unit = ???
}