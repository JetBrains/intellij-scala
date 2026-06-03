import java.util
import scala.collection.JavaConversions._
val list: util.List[_] = null
list.map(/*start*/_.asInstanceOf[String]/*end*/)
//Any => String