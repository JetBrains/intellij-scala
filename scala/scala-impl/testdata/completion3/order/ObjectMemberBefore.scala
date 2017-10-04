object ObjectMemberBefore{
  def foo = "get data"
  val format = 56
}

class Test{
  ObjectMemberBefore.<caret>
}
