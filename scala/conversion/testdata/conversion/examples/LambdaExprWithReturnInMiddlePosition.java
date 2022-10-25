import java.util.stream.LongStream;

public class JavaClass {
    void foo() {
        LongStream stream = "123".chars().asLongStream();

        //map
        stream.map(x -> {
            if (2 + 2 == 5) {
                return x + 1;
            }
            System.out.println();
            return x;
        });
        stream.map(x -> {
            if (2 + 2 == 5) {
                switch (42) {
                    default:
                        if (0 == 1) {
                            return x + 1;
                        }
                }
            }
            System.out.println();
            return x;
        });

        //foreach
        stream.forEach(x -> {
            System.out.println();
            if (2 + 2 == 5) {
                return;
            }
            System.out.println();
        });
    }
}
/*
import java.util.stream.LongStream

class JavaClass {
  def foo(): Unit = {
    val stream = "123".chars.asLongStream
    //map
    stream.map((x: Long) => {
      def foo(x: Long) = {
        if (2 + 2 == 5) return x + 1
        System.out.println()
        x
      }

      foo(x)
    })
    stream.map((x: Long) => {
      def foo(x: Long) = {
        if (2 + 2 == 5) 42 match {
          case _ =>
            if (0 == 1) return x + 1
        }
        System.out.println()
        x
      }

      foo(x)
    })
    //foreach
    stream.forEach((x: Long) => {
      def foo(x: Long) = {
        System.out.println()
        if (2 + 2 == 5) return
        System.out.println()
      }

      foo(x)
    })
  }
}
*/