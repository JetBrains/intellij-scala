def foo[A : Ordering] = 1

foo(<caret>)
//TEXT: [A: Ordering](implicit ordering$A$0: Ordering[A]), STRIKEOUT: false