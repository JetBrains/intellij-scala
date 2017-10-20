import java.util.Random

/*start*/object NameConflicts {

  val jRandom: Random = ???
  val bd: BigDecimal = ???

  val scRandom: scala.util.Random = ???
  val jBd: java.math.BigDecimal = ???
  val concMap: scala.collection.concurrent.Map[AnyRef, AnyRef] = ???
}/*end*/

/*
import java.util.Random

import scala.collection.concurrent

object NameConflicts {

  val jRandom: Random = ???
  val bd: BigDecimal = ???

  val scRandom: util.Random = ???
  val jBd: java.math.BigDecimal = ???
  val concMap: concurrent.Map[AnyRef, AnyRef] = ???
}
*/