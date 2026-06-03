def foo[A <% Ordered](x: Int) = 1

foo(<caret>)
//TEXT: [A <% Ordered](x: Int), STRIKEOUT: false