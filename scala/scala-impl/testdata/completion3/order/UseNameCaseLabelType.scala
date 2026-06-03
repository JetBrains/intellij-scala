class Frost {
}

class BadFrost extends Frost {
}

object UseNameCaseLabelType {
  def foo(frost: Frost): Unit = {
    frost match {
      case badfrost: <caret>
    }
  }
}