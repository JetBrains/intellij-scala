object SCL3385 {
  case class JObject(f: List[JField])

  case class JField(s: String, x: JParent)

  class JParent

  case class JDouble(d: Double) extends JParent

  case class JString(s: String) extends JParent

  case class Trip(id: String, price: Double, duration: Double)
  private def getCheapestTripId(trips: List[JObject]) : String = {

    val cheapestTrips = for {
      JObject(trip) <- trips
      JField("Price", JDouble(price)) <- trip
      JField("Id", JString(id)) <- trip
    } yield Trip(id, price, 0)

    val list = (cheapestTrips sortWith (/*start*/_.price < _.price/*end*/)).asInstanceOf[List[Trip]]
    return list.head.id
  }
}
//(SCL3385.Trip, SCL3385.Trip) => Boolean