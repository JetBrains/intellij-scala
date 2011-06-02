def foo[T](implicit x: T): T = x

implicit val z: String = ""
implicit val zz: Int = 1
val x: String = /*start*/foo/*end*/
//String