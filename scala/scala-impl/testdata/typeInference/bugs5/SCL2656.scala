val iteratorA = Iterator("a","b","c")
val iteratorB = Iterator("x","y","z")
/*start*/iteratorA.zipAll(iteratorB, "1", "2")/*end*/
//Iterator[(String, String)]