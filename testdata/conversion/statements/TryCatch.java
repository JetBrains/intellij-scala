public class TestJava {
    public static void main(String[] args) {
        /*start*/
        try {
        } catch (ClassCastException e) {
            System.out.println(e.getMessage);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage);
        } finally {
            System.out.println("c");
        }
        /*end*/
    }
}

/*
try {
}
catch {
  case e: ClassCastException => System.out.println(e.getMessage)
  case e: RuntimeException => System.out.println(e.getMessage)
} finally System.out.println("c")
*/