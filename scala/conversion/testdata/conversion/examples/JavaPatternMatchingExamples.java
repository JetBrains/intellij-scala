//Examples taken from https://dev.java/learn/pattern-matching/
//
// !!! ATTENTION !!!
// Current expected the result output is very shitty.
// TODO: https://youtrack.jetbrains.com/issue/SCL-21510/java-to-scala-converter-handle-latest-Java-pattern-matching-syntax
public class JavaPatternMatchingExamples {
    //Java 16
    void example_instanceOf_VariableSimple(Object obj) {
        if (obj instanceof String str) {
            System.out.println(str.toUpperCase());
        }
    }

    void example_instanceOf_VariableSimple_InExtraParentheses(Object obj) {
        if (obj instanceof (String str)) {
            System.out.println(str.toUpperCase());
        }
        if (obj instanceof ((String str))) {
            System.out.println(str.toUpperCase());
        }
    }

    void example_instanceOf_UsingVariable(Object obj) {
        if (obj instanceof String s && !s.isEmpty() && !(s.length() == 42)) {
            int length = s.length();
            System.out.println(length);
        }
    }

    void example_instanceOf_UsingVariable_MoreConditionsBeforeInstanceOf(Object obj) {
        if (2 + 2 == 42 && obj instanceof String s && !s.isEmpty() && s.length() >= 42) {
            int length = s.length();
            System.out.println(length);
        }
    }

    public void example_instanceOf_NoNeedToCastAfterCheckingTypeBefore(Object o) {
        if (!(o instanceof String s)) {
            return;
        }

        System.out.println("This is a String of length " + s.length());
    }

    public static class PointClass {
        private int x, y;

        public boolean equals(Object o) {
            return o instanceof PointClass point &&
                    x == point.x &&
                    y == point.y;
        }
    }

    //Java 20 preview
    public void example_switch(Object obj) {
        String formatter = switch (obj) {
            case Integer i -> String.format("int %d", i);
            case Long l -> String.format("long %d", l);
            case Double d -> String.format("double %f", d);
            case Object o -> String.format("Object %s", o.toString());
        };
    }

    public void example_switch_guarded_pattern(Object obj) {
        String formatter = switch (obj) {
            case String s when !s.isEmpty() -> String.format("Non-empty string %s", s);
            case Object o -> String.format("Object %s", o.toString());
        };
    }

    record Point(double x, double y) { }
    record Circle(Point center, double radius) { }

    public void example_record_pattern(Object obj) {
        if (obj instanceof Point(double x, double y)) {
            System.out.println(x + y);
        }
    }

    public void example_record_pattern_var(Object obj) {
        if (obj instanceof Point(var x, var y)) {
            System.out.println(x + y);
        }
    }

    public void example_record_pattern_var_nested(Object obj) {
        if (obj instanceof Circle(Point(var x, var y), var radius)) {
            System.out.println(x + y + radius);
        }
    }

    public void example_for() {
        java.util.List<Point> points = null;
        for (Point(double x, double y): points) {
            System.out.println(x + y);
        }
    }

    record Box(Object o) {}
}
/*
//Examples taken from https://dev.java/learn/pattern-matching/
//
// !!! ATTENTION !!!
// Current expected the result output is very shitty.
// TODO: https://youtrack.jetbrains.com/issue/SCL-21510/java-to-scala-converter-handle-latest-Java-pattern-matching-syntax
object JavaPatternMatchingExamples {
  class PointClass {
    private val x: Int = 0
    private val y: Int = 0

    override def equals(o: AnyRef): Boolean = o.isInstanceOf[JavaPatternMatchingExamples.PointClass] && x == point.x && y == point.y
  }

  final class Point(x: Double, y: Double) {
    this.x = x
    this.y = y
    final private val x: Double = .0
    final private val y: Double = .0
  }

  final class Circle(center: JavaPatternMatchingExamples.Point, radius: Double) {
    this.center = center
    this.radius = radius
    final private val center: JavaPatternMatchingExamples.Point = null
    final private val radius: Double = .0
  }

  final class Box(o: AnyRef) {
    this.o = o
    final private val o: AnyRef = null
  }
}

class JavaPatternMatchingExamples {
  //Java 16
  def example_instanceOf_VariableSimple(obj: AnyRef): Unit = {
    if (obj.isInstanceOf[String]) System.out.println(str.toUpperCase)
  }

  def example_instanceOf_VariableSimple_InExtraParentheses(obj: AnyRef): Unit = {
    if (obj.isInstanceOf[]) System.out.println(str.toUpperCase)
    if (obj.isInstanceOf[]) System.out.println(str.toUpperCase)
  }

  def example_instanceOf_UsingVariable(obj: AnyRef): Unit = {
    if (obj.isInstanceOf[String] && !s.isEmpty && !(s.length == 42)) {
      val length: Int = s.length
      System.out.println(length)
    }
  }

  def example_instanceOf_UsingVariable_MoreConditionsBeforeInstanceOf(obj: AnyRef): Unit = {
    if (2 + 2 == 42 && obj.isInstanceOf[String] && !s.isEmpty && s.length >= 42) {
      val length: Int = s.length
      System.out.println(length)
    }
  }

  def example_instanceOf_NoNeedToCastAfterCheckingTypeBefore(o: AnyRef): Unit = {
    if (!o.isInstanceOf[String]) return
    System.out.println("This is a String of length " + s.length)
  }

  //Java 20 preview
  def example_switch(obj: AnyRef): Unit = {
    val formatter: String = obj match {
      case Integer i => String.format("int %d", i)
      case Long l => String.format("long %d", l)
      case Double d => String.format("double %f", d)
      case Object o => String.format("Object %s", o.toString)
    }
  }

  def example_switch_guarded_pattern(obj: AnyRef): Unit = {
    val formatter: String = obj match {
      case String s if !s.isEmpty => String.format("Non-empty string %s", s)
      case Object o => String.format("Object %s", o.toString)
    }
  }

  def example_record_pattern(obj: AnyRef): Unit = {
    if (obj.isInstanceOf[]) System.out.println(x + y)
  }

  def example_record_pattern_var(obj: AnyRef): Unit = {
    if (obj.isInstanceOf[]) System.out.println(x + y)
  }

  def example_record_pattern_var_nested(obj: AnyRef): Unit = {
    if (obj.isInstanceOf[]) System.out.println(x + y + radius)
  }

  def example_for(): Unit = {
    val points: util.List[JavaPatternMatchingExamples.Point] = null
    import scala.collection.JavaConversions._
    for (
    <- points
    )
    {
      System.out.println(x + y)
    }
  }
}
 */