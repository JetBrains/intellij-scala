object SCL8280 {
  trait Conversions {

    implicit class ToStringConverter(value : Any)(implicit prefix: String = "") {
      def convertToString = prefix + value.toString
    }
  }

  class ImplicitSpec extends Conversions {

    /*start*/1.convertToString/*end*/
  }
}
//String