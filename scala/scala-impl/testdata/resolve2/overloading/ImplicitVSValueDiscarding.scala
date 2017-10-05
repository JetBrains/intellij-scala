object Test extends Application {
  def foo(x: Unit): Int = {print(1); 2}
  def foo(x: String): Int = {print(4); 1}

  implicit def byte2string(b: Byte): String = " "

  /* line: 3 */foo(3: Byte)
}