val /*caret*/toInline = "CAB"
val x = s"some text $toInline and some more"
val y = s"another text $toInline and ${2 + 5}"
/*
val x = "some text CAB and some more"
val y = s"another text CAB and ${2 + 5}"
*/