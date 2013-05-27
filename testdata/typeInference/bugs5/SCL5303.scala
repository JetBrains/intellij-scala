import scala.beans.BeanProperty

object SCL5303 {
class TestClass {
  @BeanProperty
  var id: Option[Long] = None

  def setId(b: Boolean) = b
}

object A {
  val x = new TestClass
  val i : Some[Long] = null
  /*start*/x. setId( i )/*end*/ // [1]
}
}
//Unit