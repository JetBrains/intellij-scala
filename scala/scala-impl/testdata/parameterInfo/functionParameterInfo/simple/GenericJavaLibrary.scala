val x = new java.util.ArrayList[Int](1)
x.add(<caret>)
/*
TEXT: e: Int, STRIKEOUT: false
TEXT: index: Int, element: Int, STRIKEOUT: false
<--->
TEXT: e: Int, STRIKEOUT: false
TEXT: i: Int, e: Int, STRIKEOUT: false
*/