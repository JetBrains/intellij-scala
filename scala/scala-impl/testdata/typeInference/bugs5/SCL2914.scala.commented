def process[A](filter:A=>Boolean)(list:List[A]):List[A] = {
  lazy val recurse = process(filter) _

  /*start*/recurse/*end*/
  recurse(list)
}
//(scala.List[A]) => scala.List[A]