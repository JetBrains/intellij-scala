val s = ""
s.indexOf(<caret>)
/*
TEXT: [B >: Char](elem: B), STRIKEOUT: false
TEXT: [B >: Char](elem: B, from: Int), STRIKEOUT: false
TEXT: ch: Int, STRIKEOUT: false
TEXT: ch: Int, fromIndex: Int, STRIKEOUT: false
TEXT: str: String, STRIKEOUT: false
TEXT: str: String, fromIndex: Int, STRIKEOUT: false
<--->
TEXT: [B >: Char](elem: B), STRIKEOUT: false
TEXT: [B >: Char](elem: B, from: Int), STRIKEOUT: false
TEXT: i: Int, STRIKEOUT: false
TEXT: i: Int, i1: Int, STRIKEOUT: false
TEXT: s: String, STRIKEOUT: false
TEXT: s: String, i: Int, STRIKEOUT: false
*/