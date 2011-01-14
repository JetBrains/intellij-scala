package java;

import java.lang.SuppressWarnings;

@SuppressWarnings(value = "foo")
@SuppressWarnings("bar")
public class Annotated {

    @SuppressWarnings("bar", x = 1)
    public static void main(String[] args) {

    }
}
/*
package java


import java.lang.SuppressWarnings


@SuppressWarnings(value = "foo")
@SuppressWarnings("bar") object Annotated {
  @SuppressWarnings("bar", x = 1) def main(args: Array[String]): Unit = {
  }
}
*/