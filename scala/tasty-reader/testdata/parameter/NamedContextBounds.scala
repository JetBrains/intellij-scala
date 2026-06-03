package parameter

trait NamedContextBounds {
  def single[A: Ordering as x]: Unit = ???

  def multipleParameters1[A: Ordering as x, B: PartialOrdering]: Unit = ???

  def multipleParameters2[A: Ordering, B: PartialOrdering as y]: Unit = ???

  def multipleParameters3[A: Ordering as x, B: PartialOrdering as y]: Unit = ???

  def multipleBounds1[A: Ordering as x: PartialOrdering]: Unit = ???

  def multipleBounds2[A: Ordering: PartialOrdering as y]: Unit = ???

  def multipleBounds3[A: Ordering as x: PartialOrdering as y]: Unit = ???

  def anonymous[A: Ordering]: Unit = ???

  def valueParameter1[A](implicit x: Ordering[A]): Unit = ???

  def valueParameter2[A](using x: Ordering[A]): Unit = ???
}