object test {
  type FA[A] = PartialFunction[A, Any]

  {
    case x => /*start*/x/*end*/
  }: FA[Int]
}

// Int