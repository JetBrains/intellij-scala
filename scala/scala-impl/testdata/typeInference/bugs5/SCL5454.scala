object K {
  implicit final class RichIterable[A](val it: Iterable[A]) extends AnyVal {
    def mean(implicit num: Fractional[A]): A = {
      var sum   = num.zero
      var size  = num.zero
      val one   = num.one
      import num.mkNumericOps
      it.foreach { e =>
        /*start*/sum.+(e)/*end*/
        sum  += e        // wrong red highlight
        size += one      // wrong red highlight
      }
      sum / size
    }
  }
}
//A