object test {
  type FIntInt = (Int => Int);

  {
    case x => /*start*/x/*end*/
  }: FIntInt
}

// Int