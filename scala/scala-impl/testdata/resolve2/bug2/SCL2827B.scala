object X {
  val x = false
  val xx = 0
}
import X.{x => xx,  _}
/*resolved: true, name: x, line: 2*/xx