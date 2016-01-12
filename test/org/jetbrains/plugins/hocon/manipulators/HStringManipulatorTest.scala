package org.jetbrains.plugins.hocon.manipulators

import org.jetbrains.plugins.hocon.TestSuiteCompanion
import org.jetbrains.plugins.hocon.psi.HString
import org.junit.runner.RunWith
import org.junit.runners.AllTests

object HStringManipulatorTest extends TestSuiteCompanion[HStringManipulatorTest]

@RunWith(classOf[AllTests])
class HStringManipulatorTest extends HoconManipulatorTest[HString]("string")
