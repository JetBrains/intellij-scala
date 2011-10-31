val someList = List(1, 2, 3)
someList match {
  case x@(hd :: t) =>
    /*start*/x.head/*end*/
}
//Int