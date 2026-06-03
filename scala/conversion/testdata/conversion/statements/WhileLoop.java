public class WhileLoop {
    void foo2() {
        // initialization expression
        int x = 1, sum = 0;

        // Exit when x becomes greater than 4
        while (x <= 10) {
            // summing up x
            sum = sum + x;

            // Increment the value of x for
            // next iteration
            x++;
        }
        System.out.println("Summation: " + sum);
    }
}
/*
class WhileLoop {
  def foo2(): Unit = {
    // initialization expression
    var x = 1
    var sum = 0
    // Exit when x becomes greater than 4
    while (x <= 10) {
      // summing up x
      sum = sum + x
      // Increment the value of x for
      // next iteration
      x += 1
    }
    System.out.println("Summation: " + sum)
  }
}
*/