public class TryMultipleCatch {
    public static void main(String[] args) {
        /*start*/
        try {
        } catch (ClassCastException | RuntimeException e) {
            System.out.println(e.getMessage);
            e.printStackTrace();
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
  case e@(_: ClassCastException | _: RuntimeException) =>
    System.out.println(e.getMessage)
    e.printStackTrace()
} finally System.out.println("c")
*/