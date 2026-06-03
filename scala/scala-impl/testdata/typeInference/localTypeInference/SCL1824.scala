val myMap = Map(1 -> "a", 2-> "b", 3 -> "c")
/*start*/myMap groupBy {_._2}/*end*/
//Map[String, Map[Int, String]]