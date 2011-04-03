import collection.generic.CanBuildFrom
/*start*/(for {i <- List(0)} yield i)(null.asInstanceOf[CanBuildFrom[List[Int], Char, Set[Char]]])/*end*/
//Set[Char]