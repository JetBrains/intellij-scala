class MySuperClass {}
class Fast {}

object UseNameInAssignement {
  val superClass = {
    var fast: Fast = null
    fast = new <caret>
  }
}