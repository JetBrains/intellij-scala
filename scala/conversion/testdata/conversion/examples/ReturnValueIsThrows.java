public class VariableWithNullValue {
    public String methodPublic() { throw new RuntimeException(); };
    protected String methodProtected() { throw new RuntimeException(); };
    private String methodPrivate() { throw new RuntimeException(); };

    private String methodWithThrowsInTheEndOfBody() {
        System.out.println();
        throw new RuntimeException();
    }
}
/*
class VariableWithNullValue {
  def methodPublic: String = throw new RuntimeException

  protected def methodProtected: String = throw new RuntimeException

  private def methodPrivate: String = throw new RuntimeException

  private def methodWithThrowsInTheEndOfBody: String = {
    System.out.println()
    throw new RuntimeException
  }
}
*/