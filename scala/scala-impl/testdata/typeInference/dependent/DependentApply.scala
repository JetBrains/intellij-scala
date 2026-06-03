package org.otus

trait StateTrait {

  case class State(value: Int)

}

class StateClass extends StateTrait {
  val sendState: (State) => Unit = (_: State) => {}

  def doIt(value: Int) {
    sendState(/*start*/State(value)/*end*/)
    val x: State = new State(1)
    x.copy(1)
  }
}
//StateClass.this.State