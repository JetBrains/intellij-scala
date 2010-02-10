package org.jetbrains.plugins.scala.lang.resolve2;

/**
 * Pavel.Fatin, 08.02.2010
 */
public class Test {
  void f(String s) {

  }

  class Inner {
    void f(String s1, String s2) {

    }

    void bar() {
//      f("");
      f("", "");
    }
  }
}
