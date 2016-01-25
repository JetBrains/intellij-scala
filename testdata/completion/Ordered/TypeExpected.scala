class TypeExpected {
  type fiInClassType = Long

  val field1 = 45
  val field2 = 33

  def fiFoo = ???


  def foo2(fiParam: Int) = {
    type fiType = Int
    trait fiTrait
    case class fiCase()

    val fiValue = 67
    val variable: fi/*caret*/
  }
}
/*
fiCase
fiTrait
fiType
fiInClassType
fiParam
fiValue
field1
field2
fiCase
*/