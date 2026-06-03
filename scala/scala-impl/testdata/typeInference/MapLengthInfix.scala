val l = List("a", "bb", "ccc")
/*start*/l zip (l map (_.length))/*end*/
//List[(String, Int)]