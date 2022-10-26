import java.util.Optional;
import java.util.function.Predicate;

class LambdaExpression {
    public void main(String[] args) {
        Optional<Integer> integers = Optional.of(12);
        integers.filter((Integer i) -> booleanFunction(i));

        integers.filter((i) -> booleanFunction(i));

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

    int example(Optional<Integer> numbers, Predicate<Integer> p) {
        return 0;
    }

    boolean booleanFunction(int i) {
        return i % 2 == 0;
    }
}
/*
import java.util.Optional
import java.util.function.Predicate

class LambdaExpression {
  def main(args: Array[String]): Unit = {
    val integers: Optional[Integer] = Optional.of(12)
    integers.filter((i: Integer) => booleanFunction(i))
    integers.filter((i: Integer) => booleanFunction(i))
    example(integers, (n: Integer) => {
      System.out.println("test")
      if (n > 5) true
      else n % 2 == 0
    })
    example(integers, (n: Integer) => {
      true
    })
    example(integers, (n: Integer) => {
      if (n > 5) true
      else n % 2 == 0
    })
  }

  def example(numbers: Optional[Integer], p: Predicate[Integer]): Int = 0

  def booleanFunction(i: Int): Boolean = i % 2 == 0
}
*/