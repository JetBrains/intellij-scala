import java.io.File

object JavaArrayType {
val sink : File = null

def foo(x: AnyRef) = 1
def foo(x : String) = "text"

/*start*/foo(if (true) sink.listFiles() else "text")/*end*/
}
//Int