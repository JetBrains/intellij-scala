// Notification message: Removed 2 imports, added 1 import
package example.bug

import java.lang     as      la
import scala.concurrent.Future
import java.util.concurrent.CompletableFuture

class TestImports {
  def clientStateSurface(): Future[la.String] = ???
}
/*package example.bug

import java.lang as la
import scala.concurrent.Future

class TestImports {
  def clientStateSurface(): Future[la.String] = ???
}
*/