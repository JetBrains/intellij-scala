def m(arr1: Array[Int], arr2: Array[Int]) = {
	/*start*/for (item1 <- arr1; item2 <- arr2 if item1 == item2) yield item1/*end*/
}
//Array[Int]