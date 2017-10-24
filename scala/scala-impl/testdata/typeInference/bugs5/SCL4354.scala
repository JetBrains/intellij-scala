object SCL4354 {
  import java.util
  import scala.collection.JavaConverters._

  implicit def castingScalaListConversion(javaList: util.List[_]): {def asScalaListOf[T]: List[T]} = new {
    def asScalaListOf[T]: List[T] = /*start*/javaList.asInstanceOf[util.List[T]].asScala.toList/*end*/
  }
}
//List[T]