import java.util.ArrayList;
import java.util.List;

class A {
  public static void main(String[] args) {
    /*start*/
    List<Integer> l = new ArrayList<Integer>()
    for (Integer integer : l) {
      System.out.println(integer);
    }
    /*end*/
  }
}
/*
val l: util.List[Integer] = new util.ArrayList[Integer]

import scala.collection.JavaConversions._

for (integer <- l) {
  System.out.println(integer)
}
*/