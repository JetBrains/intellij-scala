object SCL10387 {
  implicit class SeqOps[A](in: Seq[A]) {
    def `\\` (index: Int): A = in(index)
  }

  List(1, 2, 3) <ref>\ 0 // red

}