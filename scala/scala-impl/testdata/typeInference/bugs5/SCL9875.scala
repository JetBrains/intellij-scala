import scala.collection.JavaConversions._

class SCL9875 {
  def aMethod(template: String, templateValues: Map[String, String]): Unit = {
    import java.util.{Map => JMap}
    val body: JMap[String, String] = templateValues
    val substitutor = new SCL9875Helper(/*start*/body.asInstanceOf[JMap[String, String]]/*end*/, "{{")
  }
}

//util.Map[String, V]