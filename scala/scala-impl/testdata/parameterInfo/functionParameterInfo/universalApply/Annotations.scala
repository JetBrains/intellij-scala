class Foo
class Thing
class What

case class Bar(@Foo thing: Thing, @Foo what: What)

Bar(<caret>)
/*
thing: Thing, what: What
*/