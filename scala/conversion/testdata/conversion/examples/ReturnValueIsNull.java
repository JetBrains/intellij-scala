~~public class VariableWithNullValue {
    public String fieldPublicNull = null;
    protected String fieldProtectedNull = null;
    private String fieldPrivateNull = null;

    public String methodPublicNull() { return null; };
    protected String methodProtectedNull() { return null; };
    private String methodPrivateNull() { return null; };

    public void foo() {
        String localValuePublicNull = null;

        fieldPublicNull = "1";
        fieldProtectedNull = "1";
        fieldPrivateNull = "1";
        localValuePublicNull = "1";

        fieldPublicNull.length();
        fieldProtectedNull.length();
        fieldPrivateNull.length();
        localValuePublicNull.length();
    }
}
/*
class VariableWithNullValue {
  var fieldPublicNull: String = null
  protected var fieldProtectedNull: String = null
  private var fieldPrivateNull: String = null

  def methodPublicNull: String = null

  protected def methodProtectedNull: String = null

  private def methodPrivateNull: String = null

  def foo(): Unit = {
    var localValuePublicNull: String = null
    fieldPublicNull = "1"
    fieldProtectedNull = "1"
    fieldPrivateNull = "1"
    localValuePublicNull = "1"
    fieldPublicNull.length
    fieldProtectedNull.length
    fieldPrivateNull.length
    localValuePublicNull.length
  }
}
*/