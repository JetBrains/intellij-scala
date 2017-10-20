import scala.language.{existentials, reflectiveCalls}

object SCL9681 {

  trait Observable {
    type Handle <: {def remove(): Unit}

    def observe(callback: this.type => Unit): Handle = ???

    def unobserve(handle: Handle): Unit = ???
  }

  trait Dependencies {
    type Ref = x.Handle forSome { val x: Observable }
    var handles = List[Ref]()

    protected def addHandle(handle: Ref): Unit = {
      handles :+= handle
    }

    def addHandle(x: Boolean): Boolean = false

    protected def removeDependencies() {
      handles.foreach(_.remove())
      handles = List()
    }

    protected def observe[T <: Observable](obj: T)(handler: T => Unit): Ref = {
      val ref = obj.observe(handler)
      /*start*/addHandle(ref)/*end*/     // Cannot resolve reference addHandle with such signature
      ref
    }  // expression of type obj.handle does not conform to expected type Dependencies.this.Ref
  }
}
//Unit