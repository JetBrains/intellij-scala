val x: PartialFunction[Any, Int] = {
  case _ => 233
}
val a: (Any) => Boolean = x.isDefinedAt
//True