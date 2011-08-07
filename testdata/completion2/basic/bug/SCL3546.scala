class C(private[this] val abcdef: Any)
new C(abcde/*caret*/ = 0)
/*class C(private[this] val abcdef: Any)
new C(abcdef/*caret*/ = 0)*/