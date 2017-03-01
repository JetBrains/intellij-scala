package java;

import java.lang.SuppressWarnings;

@SuppressWarnings(value = "foo")
@SuppressWarnings("bar")
public class Annotated {

    @SuppressWarnings(value = "bar", x = 1, array={1,2,3}, inner = @SuppressWarnings)
    public static void main(final @SuppressWarnings("baz") @Deprecated String[] args) {

    }
}
/*
package java

import java.lang.SuppressWarnings


@SuppressWarnings(value = Array("foo"))
@SuppressWarnings(Array("bar")) object Annotated {
  @SuppressWarnings(value = Array("bar"), x = 1, array = Array(1, 2, 3), inner = new SuppressWarnings) def main(@SuppressWarnings(Array("baz")) @deprecated args: Array[String]): Unit = {
  }
}
*/