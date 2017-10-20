package org.jetbrains.plugins.hocon
package manipulators

import org.jetbrains.plugins.hocon.psi.HKey
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class HKeyManipulatorTest extends HoconManipulatorTest(classOf[HKey], "key")

object HKeyManipulatorTest extends TestSuiteCompanion[HKeyManipulatorTest]
