/*  
  This script replaces CRLFs by LFs in all text files within root directory (recursively)

  To remove all CRLFs from Git repository, do the following:
    1) Set core/autocrlf = false in Git config
    2) Clone the project into separated directory ("root")
    3) Update Root value (below) and run the script
    4) Run Git commit and push commands (git commit -a -m "CRLFs -> LFs", git push)
    5) Restore Git config (core/autocrlf = true)  
*/

// Update before using the script
val Root = "d:/plugin/scala-plugin/"
val NamePattern = """.*\.(scala|java|xml|properties|iml|ipr|ft|test|form|build|flex|html|log|skeleton)$""".r

import java.io._

def collect(dir: File): Seq[File] = {
  val (dirs, files) = dir.listFiles.partition(_.isDirectory)
  files.filter(f => NamePattern.findFirstIn(f.getName).isDefined) ++ dirs.flatMap(collect(_)) 
}

collect(new File(Root)).foreach { file =>
  val s = read(file)
  if(s.contains("\r\n")) {
    write(file, s.replaceAll("\r\n", "\n"))
  }
}

def read(file: File) = {
  val stream = new BufferedInputStream(new FileInputStream(file))
  val builder = new StringBuilder
  var continue = true
  while(continue) {
    val c = stream.read
    if(c == -1) {
      continue = false
    } else {
      builder.append(c.asInstanceOf[Char])
    }
  }
  stream.close
  builder.toString
}

def write(file: File, data: String) {
  val writer = new BufferedWriter(new FileWriter(file))
  writer.write(data, 0, data.size)
  writer.close()
}
