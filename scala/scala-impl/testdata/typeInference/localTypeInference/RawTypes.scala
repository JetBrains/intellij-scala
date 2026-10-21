import java.util
import scala.collection.JavaConverters._
val list: util.List[_] = null
list.asScala.map(/*start*/_.asInstanceOf[String]/*end*/)
//Any => String