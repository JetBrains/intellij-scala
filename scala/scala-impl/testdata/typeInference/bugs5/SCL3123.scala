1
case class Values(ds: Double*)

object MatchValues {
  def main(args: Array[String]) {
    val vals = Values(1.5, -2.3, 4.5)
    println(vals match {
      case Values(ds@_*) => /*start*/(0.0 /: ds)(_ + _)/*end*/   // wrong errors here
    })
  }
}
//Double