package org.jetbrains.plugins.scala
package testingSupport
package specs2
package specs2_2_11_2_4_15

import org.junit.experimental.categories.Category

/**
 * @author Roman.Shein
 * @since 27.01.2015.
 */
@Category(Array(classOf[FlakyTests])) // works locally, may fail on server
class Specs2_2_11_2_4_15_GoToSourceTest extends Specs2GoToSourceTest with Specs2_2_11_2_4_15_Base
