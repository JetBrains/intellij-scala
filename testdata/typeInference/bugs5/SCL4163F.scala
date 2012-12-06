object AbstractTypes {
  def process[T, I <: Iterator[T]](its: List[I]): List[I] = its match {
    case Nil => Nil
    case h :: t => {
      println(h.toList)
      /*start*/process(if (false) its else t)/*end*/
    }
  }
}
//List[I]