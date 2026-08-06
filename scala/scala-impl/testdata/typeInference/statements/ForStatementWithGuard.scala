val a: Array[(String, Int)] = Array.empty
/*start*/for ((x, y) <- a if y != 1) yield x/*end*/
//Array[String]