def /*caret*/emphasize(name: String, doIt: String): String = s"$doIt, $name, $doIt!"
val forrest = "Forrest"
emphasize("Forrest", "Run")
emphasize(forrest, "Run")
/*
val forrest = "Forrest"
"Run, Forrest, Run!"
s"Run, $forrest, Run!"
*/