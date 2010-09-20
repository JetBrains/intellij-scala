def main(args: Array[String]) {
  val int = 100
  def bug(f: () => Double) = {
    println(f())
  }
  bug(/*start*/() => int/*end*/) // <- plugin confused by conversion from int to double
}
//() => Double