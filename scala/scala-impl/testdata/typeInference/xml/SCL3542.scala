val catalog =
  <catalog>
    <cctherm>
      <description>hot dog #5</description>
      <yearMade>1952</yearMade>
      <dateObtained>March 14, 2006</dateObtained>
      <bookPrice>2199</bookPrice>
      <purchasePrice>500</purchasePrice>
      <condition>9</condition>
    </cctherm>
  </catalog>

catalog match {
  case <catalog>{therms @ _*}</catalog> =>
    for (therm <- therms) {
      /*start*/therm/*end*/
      println("processing: "+ (therm \ "description").text)          //<� Error message: "cannot resolve symbol \"
    }
}
//Node