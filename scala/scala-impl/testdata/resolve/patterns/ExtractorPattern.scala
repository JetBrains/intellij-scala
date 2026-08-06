val myRE = """x=([^,]+)""".r

val someX = List(
  "x=dat1",
  "x=dat2",
  "x=dat3",
  "something"
  )

for (item <- someX) {
  item match {
    case <ref>myRE(anX) => println("x was " + anX)
    case other => println("Unrecognized entry: " + other)
  }
}