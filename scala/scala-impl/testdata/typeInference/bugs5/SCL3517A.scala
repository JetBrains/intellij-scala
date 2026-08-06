def bar[A <% Int] {
  val a: A = error("")
  /*start*/a/*end*/: Int // okay
}
//Int