object ObjectGraph {
  var s: (Int, Int) = (1, 2)

  s = s match {
    case s2@(a, _) => /*start*/s2/*end*/
    case s3        => s3
  }
}
//(Int, Int)