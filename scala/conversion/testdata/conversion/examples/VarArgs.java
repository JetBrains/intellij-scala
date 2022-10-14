package java;

public class VarArgs {

    public static void x(String... args) {
        x("one", "two", "three")
    }
}
/*
package java

object VarArgs {
  def x(args: String*): Unit = {
    x("one", "two", "three")
  }
}
*/