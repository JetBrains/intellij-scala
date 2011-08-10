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
  case <catalog>{therms@_*}</catalog> =>
    (therms).foreach {
      case therm@ <cctherm>{_*}</cctherm> => println("processing: " +
        (therm \ "description").text
      )
      /*start*/therm/*end*/
    }
}
//Node