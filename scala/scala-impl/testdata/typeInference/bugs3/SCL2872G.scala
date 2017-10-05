object test {
  type FA[A] = (A => Any)

  {
    case x => /*start*/x/*end*/
  }: FA[Int]
}

// Int