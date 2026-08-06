def foo(x: Int) = 1
val u: (Int, Int) => Int = /*start*/foo(_) + foo(_)/*end*/
//(Int, Int) => Int