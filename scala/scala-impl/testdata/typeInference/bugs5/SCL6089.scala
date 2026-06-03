val unapply: PartialFunction[Char, (Int, Int) => Int] = (c: Char) => c match {
  case '+' => (x, y) => /*start*/x + y/*end*/
}
//Int