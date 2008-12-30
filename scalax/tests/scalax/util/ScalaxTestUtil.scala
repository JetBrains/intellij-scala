package scalax.util

import java.io.File
import java.net.URISyntaxException

/**
 * @author ilyas
 */

object ScalaxTestUtil {
  def getTestDataPath = {
    val loader = getClass.getClassLoader
    val resource = loader.getResource("testdata")
    try {
//      new File(resource.toURI()).getPath().replace(File.separatorChar, '/')
            "/home/ilya/work/scala/scalax/testdata"
    }
    catch {
      case _: URISyntaxException => ""
    }
  }

  def getTestName(name: String, lowercaseFirstLetter: Boolean) = {
    if (name != null && name.startsWith("test")) {
      val n1 = name.substring("test".length)
      if (lowercaseFirstLetter) {
        Character.toLowerCase(n1.charAt(0)) + n1.substring(1)
      } else n1
    } else name
  }
}