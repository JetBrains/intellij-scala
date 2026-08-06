object X {
  val x = 1
}
import X.{x => xx,  _}
/*resolved: false*/x