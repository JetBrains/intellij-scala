package org.jetbrains.plugins.scala.components.libextensions

class DynamicExtensionPoint[T](iface: Class[T]) {
    def getExtensions: Seq[T] = ???
    def getExtension: T = ???
}
