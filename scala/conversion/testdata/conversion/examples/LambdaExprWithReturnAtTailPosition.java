import java.util.stream.LongStream;

public class JavaClass {
    void foo() {
        LongStream stream = "123".chars().asLongStream();

        //map
        stream.map(x -> x);
        stream.map(x -> {
            return x;
        });
        stream.map(x -> {
            System.out.println();
            return x;
        });

        //foreach
        stream.forEach(x -> System.out.println(x));
        stream.forEach(System.out::println);
        stream.forEach(x -> {
            System.out.println(x);
        });
        stream.forEach(x -> {
            System.out.println();
            System.out.println();
        });
        stream.forEach(x -> {
            System.out.println(x);
            return;
        });
        stream.forEach(x -> {
            System.out.println();
            System.out.println();
            return;
        });
        stream.forEach(x -> {
            System.out.println();
            System.out.println();
            if (2 + 2 == 42) {
                System.out.println(1)
                return;
            }
            else {
                System.out.println(2)
                return;
            }
        });
        stream.forEach(x -> {
            System.out.println(x);
            class InnerClass {
                void innerFoo() {
                    if (true) {
                        System.out.println(1);
                        return;
                    }
                    System.out.println(2);
                }
            }
            System.out.println(3);
            return;
        });
    }
}
/*
import java.util.stream.LongStream

class JavaClass {
  def foo(): Unit = {
    val stream = "123".chars.asLongStream
    //map
    stream.map((x: Long) => x)
    stream.map((x: Long) => {
      x
    })
    stream.map((x: Long) => {
      System.out.println()
      x
    })
    //foreach
    stream.forEach((x: Long) => System.out.println(x))
    stream.forEach(System.out.println)
    stream.forEach((x: Long) => {
      System.out.println(x)
    })
    stream.forEach((x: Long) => {
      System.out.println()
      System.out.println()
    })
    stream.forEach((x: Long) => {
      System.out.println(x)
    })
    stream.forEach((x: Long) => {
      System.out.println()
      System.out.println()
    })
    stream.forEach((x: Long) => {
      System.out.println()
      System.out.println()
      if (2 + 2 == 42) System.out.println(1)
      else System.out.println(2)
    })
    stream.forEach((x: Long) => {
      System.out.println(x)
      class InnerClass {
        def innerFoo(): Unit = {
          if (true) {
            System.out.println(1)
            return
          }
          System.out.println(2)
        }
      }
      System.out.println(3)
    })
  }
}
*/