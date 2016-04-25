import scalaz.{Scalaz, \/, _}

object TestApp extends App {

  case class Address(street: \/[String,String])
  case class Job(name: \/[String,String],address: \/[String,Address])

  val job = Job("JOB-1234".right, Address("Somewhere".right).right)

  val street = job.address <ref>>= (_.street)

  println(street)
}