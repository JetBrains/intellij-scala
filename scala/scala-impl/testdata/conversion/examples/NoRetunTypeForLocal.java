public class JSome {
    public static int i;

    static {
        i = m();
    }

    public static int m() { return 123; }
}
/*
object JSome {
  var i: Int = 0

  def m: Int = 123

  try i = m

}
*/