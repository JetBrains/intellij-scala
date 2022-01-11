object opts {
  val myCustomOption = "..."
}

scalacOptions += s"${opts.<caret>}"

/*
myCustomOption
*/