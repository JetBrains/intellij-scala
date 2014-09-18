public class JSome {
  public static int i;

  static {
    i = m();
  }

  public static int m() { return 123; }
}
/*
object JSome {
  def m: Int = {
    return 123
  }

  var i: Int = 0
  try {
    i = m
  }
}
*/