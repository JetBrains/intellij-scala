import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;


class Imports {
    public static void main(String[]args){
        List<File> fList = new ArrayList<File>();
        Set<File> fSet = new HashSet<File>();
        for (File f: fList) {
            fSet.add(f);
        }
    }
}

/*
import java.io.File
import java.util


object Imports {
  def main(args: Array[String]) {
    val fList: util.List[File] = new util.ArrayList[File]
    val fSet: util.Set[File] = new util.HashSet[File]
    import scala.collection.JavaConversions._
    for (f <- fList) {
      fSet.add(f)
    }
  }
}
*/