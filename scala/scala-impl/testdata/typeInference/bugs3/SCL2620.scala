object test {
    trait Gen[T]
    val GenDouble: Gen[Double] = error("")
    trait Matcher[T]
    class Expectation[A] {
      def mustt(g: => Matcher[A]) = error("")
    }
    def passF[T](f: T => Any): Matcher[Gen[T]] = error("")
    new Expectation[Gen[Double]]().mustt(passF(x => /*start*/x/*end*/))
}
//Double