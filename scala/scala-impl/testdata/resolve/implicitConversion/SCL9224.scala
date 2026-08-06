object SCL9224 {

  abstract class ControllerScope[T](val controller: T)(implicit as: Int)

  new ControllerScope(newController) {
    controller.<ref>user
  }

  def newController = {
    new {
      val user = 1
    }
  }
}
