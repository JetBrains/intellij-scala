package org.jetbrains.plugins.hocon
package manipulators

import org.jetbrains.plugins.hocon.psi.HString
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class HStringManipulatorTest extends HoconManipulatorTest(classOf[HString], "string")

object HStringManipulatorTest extends TestSuiteCompanion[HStringManipulatorTest]
