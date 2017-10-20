public class StaticClass {
    public static String constant = "str";
}

class A {
    StaticClass a = new StaticClass();
    StaticClass b = new StaticClass();
}

/*
object StaticClass {
  var constant: String = "str"
}

class StaticClass {}

class A {
  val a: StaticClass = new StaticClass
  val b: StaticClass = new StaticClass
}
*/