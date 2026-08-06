def discardValue(p: => Unit) {}
val v = 123
discardValue(/*start*/v/*end*/)
//Int