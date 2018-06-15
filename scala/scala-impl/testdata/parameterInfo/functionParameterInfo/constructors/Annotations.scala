class Foo
class Thing
class What

case class Bar(@Foo thing: Thing, @Foo what: What)

new Bar(<caret>)
/*
thing: Thing, what: What
*/