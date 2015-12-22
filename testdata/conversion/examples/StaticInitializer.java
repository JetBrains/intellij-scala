public class JSome {
  public static int i;

  static {
    i = m();
  }

  boolean value = Boolean.TRUE;

  public static int m() { return 123; }
}
/*
object JSome {
  var i: Int = 0

  def m: Int = 123

  try i = m

}

class JSome {
  val value: Boolean = java.lang.Boolean.TRUE
}
*/