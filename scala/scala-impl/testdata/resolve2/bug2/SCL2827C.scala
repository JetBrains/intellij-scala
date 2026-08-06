object X {
  val x = 1
}
import X.{x => xx,  _}
/*resolved: true, name: x, line: 2*/xx