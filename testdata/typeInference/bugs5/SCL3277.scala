class AA {
  def + (x: AA) = 0
}

class BB {
  override def toString = /*start*/new AA + "abc"/*end*/
}
//String