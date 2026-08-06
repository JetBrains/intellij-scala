object Test {

  def method1(): Unit = println("method1")
  def method2(): Unit = println("method2")
  def method3(): Unit = println("method3")

  def main(args: Array[String]): Unit = {
    val anything: Any = 42
    anything match {
      case "empty" =>
      case "one expression" => println("42")
      case "block of expressions" =>
        method1()
        method2()
        method3()
      case inner @ "inner match" =>
        inner match {
          case "empty" =>
          case "one expression" => println("42")
          case "block of expressions" =>
            method1()
            method2()
            method3()
        }
      case "with guard" if 1 != 0 =>
      case _ => println("42")
    }
    method1() match {
      case () => "method result match"
    }
    42 match {
      case 42 => "literal match"
    }
    21 + 21 match {
      case 42 => "binary expression match"
    }
  }
}