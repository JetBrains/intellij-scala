class Foo[A : Ordering](x: Int)

new Foo(<caret>)
//TEXT: [A: Ordering](x: Int), STRIKEOUT: false