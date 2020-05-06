# TASTy Reflect & Inspect compatibility for the Scala 2.x TASTy APIs

Scala 3-like syntax on top of the ABI of [scala.tasty.Reflection.scala](https://github.com/lampepfl/dotty/blob/0.24.0-RC1/library/src/scala/tasty/Reflection.scala)

Required because Scala 2.x doesn't support extension methods (and implicit AnyVal classes in a class):
https://dotty.epfl.ch/docs/reference/contextual/relationship-implicits.html

See:
* https://dotty.epfl.ch/docs/reference/metaprogramming/tasty-reflect.html
* https://dotty.epfl.ch/docs/reference/metaprogramming/tasty-inspect.html
* https://github.com/lampepfl/dotty/tree/master/library/src/scala/tasty
* https://github.com/lampepfl/dotty/tree/master/tasty-inspector

It should be possible to compile in and use the following file from Scala 2.x:
https://github.com/lampepfl/dotty/blob/master/library/src/scala/tasty/reflect/SourceCodePrinter.scala