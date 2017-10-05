object SCL3455 {
  trait T
  val x: T = {
    object a extends T
    /*start*/a/*end*/ // Found Any, required T
  }
}
//a.type