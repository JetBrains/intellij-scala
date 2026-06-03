import _root_.qwe.C1

object Main extends Application {
  println(new C1()./*resolved: true, line: 14*/c2)
}

package qwe {

class C1 {
  def c1 = 1
}

class C2 {
  def c2 = 2
}

}

package object qwe {
  implicit def c1Toc2(ref: C1): C2 = new C2
}