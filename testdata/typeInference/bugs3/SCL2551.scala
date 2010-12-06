def foo(x: Int) = 1
/*start*/foo(for (i <- 1 to 2 if i != 3; j <- 1 to 2) yield 2)/*end*/
//Int