package org.otus

trait StateTrait {

  case class State(value: Int)

}

class StateClass extends StateTrait {
  val sendState: (State) => Unit = (_: State) => {}

  def doIt(value: Int) {
    sendState(State(value))
    val x: State = new State(1)
    /*start*/x.copy(1)/*end*/
  }
}
//StateClass.this.State