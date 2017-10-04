class Queue[T] (
  private val leading: List[T],
  private val trailing: List[T]
  ) {
    private def mirror =
      if (leading.isEmpty)
        new Queue(trailing.reverse, Nil)
      else
      /*start*/this/*end*/
}
//Queue[T]