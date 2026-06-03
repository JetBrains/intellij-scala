object Test extends Application {
  def foo(x: Int): Int = {print(1); 2}
  def foo(x: Long): Int = {print(4); 1}

  /* line: 2 */foo(3: Byte)
}