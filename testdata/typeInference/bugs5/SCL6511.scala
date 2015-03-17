trait Yo
object Dawg extends Yo

implicit class YoApply(x: Yo) {
  def apply(y: Int) = "hello"
}

/*start*/Dawg(5)/*end*/
//String