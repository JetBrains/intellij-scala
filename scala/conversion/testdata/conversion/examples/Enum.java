public enum Day {
  MONDAY, TUESDAY, WEDNESDAY;

  double calculate(double x, double y) {
    switch (this) {
      case MONDAY:
        return x + y;
      case TUESDAY:
        return x - y;
      case WEDNESDAY:
        return 2 * x;
    }
  }
}
/*
object Day extends Enumeration {
  type Day = Value
  val MONDAY, TUESDAY, WEDNESDAY = Value

  def calculate(x: Double, y: Double): Double = this match {
    case MONDAY =>
      x + y
    case TUESDAY =>
      x - y
    case WEDNESDAY =>
      2 * x
  }
}
 */