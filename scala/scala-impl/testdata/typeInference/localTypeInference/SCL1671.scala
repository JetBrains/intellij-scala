/*start*/List("1", "12", "123").reduceLeft(
  (a, b) => if (a.length > b.length) a else b
)/*end*/
//String