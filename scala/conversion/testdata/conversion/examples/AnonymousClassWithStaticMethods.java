class Scratch {
  public static void main(String[] args) {
    new Object() {
      public void foo1(String[] args) {
      }

      public static void staticFoo1(String[] args) {
      }

      public void foo2(String[] args) {
      }


      //line comment for staticFoo2
      public static void staticFoo2(String[] args) {
      }
    };
  }
}
/*
object Scratch {
  def main(args: Array[String]): Unit = {
    new AnyRef() {
      def foo1(args: Array[String]): Unit = {
      }

      def foo2(args: Array[String]): Unit = {
      }

      //TODO: 'static' modifier is not supported
      def staticFoo1(args: Array[String]): Unit = {
      }

      //line comment for staticFoo2
      //TODO: 'static' modifier is not supported
      def staticFoo2(args: Array[String]): Unit = {
      }
    }
  }
}
 */