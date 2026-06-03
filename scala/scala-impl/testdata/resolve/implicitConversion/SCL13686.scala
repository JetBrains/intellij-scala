class Actor {
  def setOnClickHandler(handler: () => Unit): Unit = {}
}

object Pimps {

  implicit class ActorPimps[A <: Actor](a: A) {
    def onClicked(code: => Unit): A = {
      a.setOnClickHandler(code _)
      a
    }
  }

}

trait PlaySoundOnClick {
  this: Actor =>

  def setup(): Unit = {
    import Pimps._
    this.<ref>onClicked {}
  }
}