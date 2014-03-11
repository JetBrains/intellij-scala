object SCL6608 {
  object Model {
    type Listener[-A] = PartialFunction[A, Unit]
  }
  trait Model[A] {
    def addListener(pf: Model.Listener[A]): pf.type = ???
  }

  trait Update
  private object Peer extends Model[Update]

  def addListener(pf: Model.Listener[Update]): pf.type = /*start*/Peer.addListener(pf)/*end*/
}
//pf.type