class A{
    final void run(){System.out.println("running...");}
}

class B extends A{
    public static void foo() {
        new B().run();
    }
}

/*
class A {
  final def run(): Unit = {
    System.out.println("running...")
  }
}

object B {
  def foo(): Unit = {
    new B().run()
  }
}

class B extends A {}
 */