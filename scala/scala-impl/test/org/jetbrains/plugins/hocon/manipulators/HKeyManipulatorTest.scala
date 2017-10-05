package org.jetbrains.plugins.hocon.manipulators

import org.jetbrains.plugins.hocon.TestSuiteCompanion
import org.jetbrains.plugins.hocon.psi.HKey
import org.junit.runner.RunWith
import org.junit.runners.AllTests

object HKeyManipulatorTest extends TestSuiteCompanion[HKeyManipulatorTest]

@RunWith(classOf[AllTests])
class HKeyManipulatorTest extends HoconManipulatorTest[HKey]("key")