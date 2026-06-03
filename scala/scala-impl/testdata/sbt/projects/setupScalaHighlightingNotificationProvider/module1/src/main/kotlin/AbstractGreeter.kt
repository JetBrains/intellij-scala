abstract class AbstractGreeter(private val str: String) : Greeter {
  override fun greeting(): String = str
}
