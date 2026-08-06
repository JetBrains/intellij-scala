abstract class ParamExtractor {
  import ParamExtractor._

  def buggedfFunction(filter: Filter) = {

    2 match {
      case filter(a) => /*start*/a/*end*/
    }
  }
}

object ParamExtractor {
  class Filter(f: (Int) => Option[Int]) {
    def unapply(t: Int): Option[Int] = f(t)
  }
}
//Int