val seq = Seq(Some(1), Some(2), Some(3))
/*start*/(seq.flatten.head,
seq.view.head,
seq.flatten.view.head,
seq.view.flatten.head)/*end*/
//(Int, Some[Int], Int, Int)