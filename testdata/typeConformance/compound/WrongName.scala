case class Bird(val name: String) extends Object {
  def fly(height: Int): Unit = {}
}
case class Plane(val callsig: String) extends Object {
  def fly(height: Int): Unit = {}
}
def takeoff(
        runway: Int,
        r: {val callsign: String; def fly(height: Int)}) = {
  tower.print(r.callsign + " requests take-off on runway " + runway)
  tower.read(r.callsign + " is clear for take-off")
  r.fly(1000)
}
val bird = new Bird("Polly the parrot") {val callsign = name}
val a380 = new Plane("TZ-987")
val a: {val callsign: String; def fly(height: Int)} = a380
//False