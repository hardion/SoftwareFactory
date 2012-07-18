package fr.soleil.software.script

import fr.soleil.software.model.SoftwareFactory
import fr.soleil.software.sonar.model.SonarServer

import fr.soleil.software.scm.model.ScmBuilder
import fr.soleil.software.scm.model.SCM
import fr.soleil.software.maven.model.MavenProject

import org.jggug.kobo.gexcelapi.GExcel

/**
 * CREATE A REPORT FROM THE QUALITY SERVER
 * 
 * Give the TOP of worst project
 */
sf = new SoftwareFactory()

//violations=["blocker_violations","major_violations","critical_violations"]

URL url = this.class.classLoader.getResource("fr/soleil/software/script/QualityReport.xls") 
def xls = GExcel.open(url.file)
// HEAD
//report."A1".value = "GroupId"
//report."B1".value = "ArtifactId"
//report."C1".value = "Developers"
//report."D1".value = "Blocker"
//report."E1".value = "Critical"
//report."F1".value = "Major"
[SonarServer.CPP_LANGUAGE, SonarServer.JAVA_LANGUAGE].each(){ language ->
  def report = xls."$language"
  int i=2
  sf.sonar.findAllProjectByMetric(language).each(){
    if(it.measures.any{ /*it.metricKey in violations &&*/ it.value > 0}){
      mvn = sf.getProjectFrom("sonar", it.key)
      
      report."A$i".value = mvn?.groupId 
      report."B$i".value = mvn?mvn.simpleArtifactId:it.key
      report."C$i".value = "${mvn?.developers}"
      report."D$i".value = it.measures[0].value
      report."E$i".value = it.measures[1].value
      report."F$i".value = it.measures[2].value
      
      i++
    }
  }
  
}



xls.write(new FileOutputStream("target/SONAR-REPORT.xls"))