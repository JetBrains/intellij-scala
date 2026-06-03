1
case class Queue[T](private val leading: List[T], private val trailing: List[T]) {
    /*start*/new Queue(trailing.reverse, Nil)/*end*/
}
//Queue[T]