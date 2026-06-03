public class TestJava {
  public void test() {
    foo();
  }

  public static int foo() {
    return 1;
  }
}
/*
object TestJava {
  def foo: Int = 1
}

class TestJava {
  def test(): Unit = {
    TestJava.foo
  }
}
*/