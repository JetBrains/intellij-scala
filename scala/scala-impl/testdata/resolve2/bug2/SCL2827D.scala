object X {
  val x = 1
}
import X.{x => x,  _}
/*resolved: true, name: x, line: 2*/x