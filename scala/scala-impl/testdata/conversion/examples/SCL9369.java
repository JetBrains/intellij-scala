class Foo {
  final int b;
  Foo(String a, int b){
    this(b);
    System.out.println(a);
  }
  Foo(int b){
    this.b = b;
  }
}

class T extends Foo {
  T(String str, int a){
    super(str, a);
  }
}


/*
class Foo(val b: Int) {
  def this(a: String, b: Int) {
    this(b)
    System.out.println(a)
  }
}

class T(val str: String, val a: Int) extends Foo(str, a) {
}
 */