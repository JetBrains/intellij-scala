import java.io.File

object CompileServerLauncher {
  def foo(x: Class[_]) = "text"
  def compilerJars = {
    val ideaRoot = new File( foo(getClass)).getParent
    val pluginRoot = new File(foo(getClass)).getParent

    /*start*/pluginRoot/*end*/
  }
}
//String