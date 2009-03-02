package simple

class Simple {
  def addOne(x: Int) = x + 1
  val one = addOne(/*caret*/)
}