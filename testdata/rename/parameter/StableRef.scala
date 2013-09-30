val a = 1
val (_, /*caret*/`a`) = (a, a)
/*
val NameAfterRename = 1
val (_, /*caret*/NameAfterRename) = (NameAfterRename, NameAfterRename)
*/