object SCL5489 extends App {
  val anonFunctions : List[() => String] = /*start*/List(func1, func2)/*end*/
  def func1() : String = "Hello"
  def func2() : String = "World"
  anonFunctions.foreach(func => println(func()))
}
//List[() => String]