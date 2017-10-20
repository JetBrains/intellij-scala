object SCL8157B extends App {
  // some implicit conversion between functions, doesn't matter its body or type
  implicit def convert(f : Int => Int): (String => String) = null

  val f: (Int => Int) = null
  /*start*/f("hello")/*end*/
}
//String