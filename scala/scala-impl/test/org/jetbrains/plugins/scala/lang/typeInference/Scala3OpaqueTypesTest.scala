package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3OpaqueTypesTest extends ImplicitParametersTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testExtensionMethodAliasOutside(): Unit = checkTextHasNoErrors(s"""
    object Scope:
      opaque type Foo = Int
      extension (that: Foo)
        def method(): Unit = ()
    (??? : Scope.Foo).method()
  """)

  def testExtensionMethodAliasInside(): Unit = checkTextHasNoErrors(s"""
    object Scope:
      opaque type Foo = Int
      extension (that: Foo)
        def method(): Unit = ()
      (??? : Foo).method()
  """)

  def testExtensionMethodTypeInside(): Unit = checkTextHasNoErrors(s"""
    object Scope:
      opaque type Foo = Int
      extension (that: Foo)
        def method(): Unit = ()
      (??? : Int).method()
  """)
}
