package example.foo

object SharedTest {
  val value: String = SharedMain.value + PlatformTest.value + GeneratedTest.value
}
