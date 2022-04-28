package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_scala_2_11_specs_2

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.specs2._
import org.jetbrains.plugins.scala.{DependencyManager, LatestScalaVersions, ScalaVersion}

trait Specs2_Scala_2_11_Specs_2_Base extends Specs2TestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_11

  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
    DependencyManager, // specs library depends on scala-reflect, do not ignore it
    ("org.specs2" %% "specs2-core" % "2.5").transitive()
  ) :: Nil
}

class Specs2_Scala_2_11_Specs_2_DuplicateConfigTest extends Specs2DuplicateConfigTest with Specs2_Scala_2_11_Specs_2_Base
class Specs2_Scala_2_11_Specs_2_FileStructureViewTest extends Specs2FileStructureViewTest with Specs2_Scala_2_11_Specs_2_Base
class Specs2_Scala_2_11_Specs_2_GoToSourceTest extends Specs2GoToSourceTest with Specs2_Scala_2_11_Specs_2_Base
class Specs2_Scala_2_11_Specs_2_ObjectSpecTest extends Specs2ObjectSpecTest with Specs2_Scala_2_11_Specs_2_Base
class Specs2_Scala_2_11_Specs_2_PackageTest extends Specs2PackageTest with Specs2_Scala_2_11_Specs_2_Base
class Specs2_Scala_2_11_Specs_2_RegExpTest extends Specs2RegExpTestNameTest with Specs2_Scala_2_11_Specs_2_Base
class Specs2_Scala_2_11_Specs_2_SCL7228Test extends SCL7228Test with Specs2_Scala_2_11_Specs_2_Base
class Specs2_Scala_2_11_Specs_2_SingleTestTest extends Specs2SingleTestTest with Specs2_Scala_2_11_Specs_2_Base
class Specs2_Scala_2_11_Specs_2_SingleTestTestDynamic extends Specs2_Scala_2_11_Specs_2_SingleTestTest {
  override val useDynamicClassPath = true
}
class Specs2_Scala_2_11_Specs_2_SpecialCharactersTest extends Specs2SpecialCharactersTest with Specs2_Scala_2_11_Specs_2_Base
class Specs2_Scala_2_11_Specs_2_StaticStringTest extends Specs2StaticStringTest with Specs2_Scala_2_11_Specs_2_Base
class Specs2_Scala_2_11_Specs_2_WholeSuiteTest extends Specs2WholeSuiteTest with Specs2_Scala_2_11_Specs_2_Base
