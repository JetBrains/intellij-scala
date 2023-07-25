class Foo
class Thing
class What

case class Bar(@Foo thing: Thing, @Foo what: What)

Bar(<caret>)
/*
TEXT: thing: Thing, what: What, STRIKEOUT: false
*/