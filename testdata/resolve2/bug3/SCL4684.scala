object SCL4684 {
  for {
    a <- Some(1)
    a <- Some(/* line: 3*/a)
    b = /* line: 4 */a
    a <- Some(/* line: 4 */a)
    b = /* line: 7 */b
    if true
  } yield /* line: 6 */a
}