package java;

import java.lang.Deprecated;
import java.lang.SuppressWarnings;

@SuppressWarnings(value = "foo")
@SuppressWarnings("bar")
public class Annotated {

    @SuppressWarnings("bar", x = 1, array={1,2,3}, inner = @SuppressWarnings)
    public static void main(final @SuppressWarnings("baz") @Deprecated String[] args) {

    }
}
/*
package java


import java.lang.Deprecated
import java.lang.SuppressWarnings


@SuppressWarnings(value = "foo")
@SuppressWarnings("bar") object Annotated {
  @SuppressWarnings("bar", x = 1, array = Array(1, 2, 3), inner = new SuppressWarnings) def main(@SuppressWarnings("baz") @Deprecated args: Array[String]): Unit = {
  }
}
*/