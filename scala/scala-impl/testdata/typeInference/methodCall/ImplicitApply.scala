import java.util.ArrayList
import collection.JavaConverters._

val list: java.util.List[Int] = new ArrayList[Int]

list.add(23)
/*start*/list.asScala(0)/*end*/
//Int