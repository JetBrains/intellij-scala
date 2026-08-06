package hello

object Hello {
  val foo = 2
  def main(args: Array[String]): Unit = {
    println("Hello, world!")
    println("Args: " + args.mkString(", "))
    println("Environment: " + sys.env.get("FOO"))
  }
}
