public class NoReturnTypePublic {
    public String getName() {
        return name;
    }

    public static int test = 45;

    @Override
    public String toString() {
        return super.toString();
    }

    protected String name = "";
}
/*
object NoReturnTypePublic {
  var test = 45
}

class NoReturnTypePublic {
  def getName = name

  override def toString = super.toString

  protected var name: String = ""
}
 */