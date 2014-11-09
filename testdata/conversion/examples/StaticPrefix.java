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
  def foo: Int = {
    return 1
  }
}

class TestJava {
  def test {
    TestJava.foo
  }
}
*/