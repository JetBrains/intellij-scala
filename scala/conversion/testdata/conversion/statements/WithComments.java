class JSome {
  final int t; //field will be dropped
  JSome(int t){
      this.t = t;
      //comments in droppped constructor
  }
  //before func
  void foo(){
      int t = 56; //last in line
      //last in func
  }
  //last in class
}

/*
class JSome(val t: Int //field will be dropped
           ) //comments in droppped constructor
{
  //before func
  def foo(): Unit = {
    val t: Int = 56 //last in line
    //last in func
  }
  //last in class
}
*/