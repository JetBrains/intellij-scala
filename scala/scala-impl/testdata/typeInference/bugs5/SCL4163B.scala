def process[A](filter:A=>Boolean)(list:List[A]):List[A] = {
  lazy val recurse = process(filter) _

  list match {
    case head::tail => if (filter(head)) {
      head::recurse(tail)
    } else {
      /*start*/recurse(tail)/*end*/
    }

    case Nil => Nil
  }
}
//List[A]