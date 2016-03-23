object SCL8242 {
  def foo(x: Float) = {
    val t: Double = 56
    if (true) x + t
    else /*start*/x/*end*/
  }
}

//Double