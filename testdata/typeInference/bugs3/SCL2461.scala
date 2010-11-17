val list = List(1, 2, 3)
val list2 = List(1, 2, 3)
val newColl = for (outer <- list; inner <- list2 if outer == inner) yield /*start*/outer + 1/*end*/
//Int