object Test extends Application {
  def foo(x: Byte): Int = {print(1); 2}
  def foo(x: String): Int = {print(4); 1}

  implicit def byte2string(b: Int): String = " "

  /* line: 3 */foo(3)
}