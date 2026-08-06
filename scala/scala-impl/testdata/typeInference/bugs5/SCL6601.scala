object SCL6601 {
  sealed trait IList {
    def ::[T](that: T) = SCL6601.::(that, this)
  }
  case object INil extends IList
  case class ::[H, T <: IList](head: H, tail: T) extends IList

  implicit def ilist0[T](
                                arg: T :: IList
                                ): T = arg.head
  implicit def ilist1[T](
                                arg: _ :: T :: IList
  ): T = arg.tail.head
  implicit def ilist2[T](
                                arg: _ :: _ :: T :: IList
  ): T = arg.tail.tail.head
  implicit def ilist3[T](
                                arg: _ :: _ :: _ :: T :: IList
  ): T = arg.tail.tail.tail.head
  implicit def ilist4[T](
                                arg: _ :: _ :: _ :: _ :: T :: IList
  ): T = {
    /*start*/arg/*end*/
    arg.tail.tail.tail.tail.head
  }
}
//_ :: _ :: _ :: _ :: T :: SCL6601.IList