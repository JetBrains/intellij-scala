class A {
  type op = Char => ((Int, Int) => Int)

  val operators: List[op] = List(c => (x, y) => /*start*/x + y/*end*/)
}
//Int