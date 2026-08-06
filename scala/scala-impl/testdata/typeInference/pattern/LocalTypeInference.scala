val l: List[PartialFunction[Any, _]] = null

l match {
  case h :: t => /*start*/h.isDefinedAt(true)/*end*/
}
//Boolean