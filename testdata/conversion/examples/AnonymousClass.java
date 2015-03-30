public class AnonymousClass {
  void foo() {
    new Runnable() {
      public void run() {

      }
    }
  }
}
/*
class AnonymousClass {
  def foo {
    new Runnable() {
      def run {
      }
    }
  }
}
 */