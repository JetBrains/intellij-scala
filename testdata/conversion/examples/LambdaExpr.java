import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/*start*/
class LambdaExpression {
    public void main(String[] args) {
        List<Integer> integers = new ArrayList<Integer>();
        integers.forEach((Integer i) -> System.out.println(i));

        integers.forEach((i) -> System.out.println(i));

        example(integers, n -> {
            System.out.println("test");
            if (n > 5) {
                return true;
            } else {
                return n % 2 == 0;
            }
        });

        example(integers, n -> {
            return true;
        });

        example(integers, n -> {
            if (n > 5) {
                return true;
            } else {
                return n % 2 == 0;
            }
        });
    }

    int example(List<Integer> numbers, Predicate<Integer> p) {
        return 0;
    }
}/*end*/

/*
class LambdaExpression {
  def main(args: Array[String]): Unit = {
    val integers: util.List[Integer] = new util.ArrayList[Integer]
    integers.forEach((i: Integer) => System.out.println(i))
    integers.forEach((i: Integer) => System.out.println(i))
    example(integers, (n: Integer) => {
      def foo(n: Integer) = {
        System.out.println("test")
        if (n > 5) true
        else n % 2 == 0
      }

      foo(n)
    })
    example(integers, (n: Integer) => {
      def foo(n: Integer) =
        true

      foo(n)
    })
    example(integers, (n: Integer) => {
      def foo(n: Integer) =
        if (n > 5) true
        else n % 2 == 0

      foo(n)
    })
  }

  def example(numbers: util.List[Integer], p: Predicate[Integer]): Int = 0
}*/