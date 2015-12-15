class C {
  C(int a, int b) {
    this(a);
  }
  C(int a) {
    System.out.println(a);
  }
  final int u= 67;
  void foo(){
    System.out.println("foo");
  }

  C(String q) {
    System.out.println(q);
    C.this.u = 34;
  }
}

/*
class C {
  def this(a: Int) {
    this()
    System.out.println(a)
  }

  def this(a: Int, b: Int) {
    this(a)
  }

  final var u: Int = 67

  def foo() {
    System.out.println("foo")
  }

  def this(q: String) {
    this()
    System.out.println(q)
    C.this.u = 34
  }
}
 */