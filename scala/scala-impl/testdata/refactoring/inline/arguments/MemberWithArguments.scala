object Main extends App {
  def /*caret*/func(x: Int) = x * 2

  val i = 10
  val i2 = func(i)
  println(i2)
}
/*
object Main extends App {

  val i = 10
  val i2 = i * 2
  println(i2)
}*/