def foo[M[_], A](ma: M[A]): A = error("stub")
val ls: List[Int] = List(1)
/*start*/foo(ls)/*end*/
//Int