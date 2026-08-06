object Main {
  def doAction(p: String) {}

  def doAction(action: => Unit) {action}

  def someAction {
    /* */doAction(someAction) // (1)
  }

  def m {
    /* */doAction(someAction) // (2)
  }
}