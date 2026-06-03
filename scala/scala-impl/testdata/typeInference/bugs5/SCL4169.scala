object Scl4169 {
  val a: Array[Any] = {
    /*start*/for (item <- List[Any]().toArray) yield ""/*end*/
  }

  val b: Array[Any] = {
    (List[Any]().toArray).map { case item => "" }
  }
}
//Array[Any]