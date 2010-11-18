val p: Seq[Double] = Seq()
p.foldLeft("") {
  (a, b) /*(B, Double) inferred*/ =>
    /*start*/(a, b)/*end*/
    0d
}
//(String, Double)