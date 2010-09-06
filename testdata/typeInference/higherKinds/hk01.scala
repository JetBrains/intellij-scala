def foo[M[_]](ma: M[_]): M[Any] = error("stub")
val ls: List[Int] = List(1)
/*start*/foo(ls)/*end*/
//List[Any]