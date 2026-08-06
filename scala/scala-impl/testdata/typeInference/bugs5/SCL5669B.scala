val permutations = (1 to 9).reverse.permutations.toStream
for (permutation <- permutations ; intPerm = permutation.mkString("").toInt if true) {
  /*start*/permutation/*end*/
  println(intPerm)
}
//IndexedSeq[Int]