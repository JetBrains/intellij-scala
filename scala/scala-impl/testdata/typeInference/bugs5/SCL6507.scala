object SCL6507 {
  import scala.Product2

  object patternMatchingProduct {

    class MyProduct(one: String, two: Int) extends Product2[String, Int]  {
      def _1 = one
      def _2 = two

      def canEqual(that: Any): Boolean = ???
    }

    object MyProduct {
      def apply(one: String, two: Int) = new MyProduct(one, two)
      def unapply(prod: MyProduct): Option[Product2[String, Int]] = Some(prod)
    }

    object MyProduct2 {
      def apply(one: String, two: Int) = new MyProduct(one, two)
      def unapply(prod: MyProduct): Option[MyProduct] = Some(prod)
    }

    val myProduct = MyProduct("one", 2)
    (myProduct, myProduct) match {
      case (MyProduct(one, two), MyProduct2(three, four)) => /*start*/(one, two, three, four)/*end*/
    }
  }
}
//(String, Int, String, Int)