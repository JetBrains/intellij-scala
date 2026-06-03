object test {
  type PFIntInt = PartialFunction[Int, Int];

  {
    case x => /*start*/x/*end*/
  }: PFIntInt
}

// Int