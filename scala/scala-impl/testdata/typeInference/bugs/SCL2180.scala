import collection.immutable.List
import collection.SeqView

object Test {
  val view: SeqView[Int] = null
  /*start*/view.map(_ * 2)/*end*/ 
}
//SeqView[Int]