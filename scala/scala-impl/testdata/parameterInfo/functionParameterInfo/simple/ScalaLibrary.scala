import scala.collection.mutable.ArrayBuffer

val buffer = new ArrayBuffer[Int]
buffer.append(<caret>)
/*
TEXT: elem: Int, STRIKEOUT: false
TEXT: elems: Int*, STRIKEOUT: true
*/