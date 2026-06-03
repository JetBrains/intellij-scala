def foo[A <% Int, B <% Int] {
  val a: A = error("")
  /*start*/a/*end*/: Int // fail: A does not conform to expected type Int
}
//Int