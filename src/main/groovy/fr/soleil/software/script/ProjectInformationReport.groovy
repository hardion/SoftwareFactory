package fr.soleil.software.script

import fr.soleil.software.model.SoftwareFactory
import fr.soleil.software.sonar.model.SonarServer
import fr.soleil.software.scm.model.SCM
import fr.soleil.software.maven.model.MavenProject

import org.jggug.kobo.gexcelapi.GExcel

/**
 * CREATE A INFORMATION REPORT FROM THE SOFTWARE FACTORY
 * 
 */
sf = new SoftwareFactory()

URL url = this.class.classLoader.getResource("fr/soleil/software/script/ProjectInformation.xls") 
def xls = GExcel.open(url.file)
// HEAD
//report."A1".value = "GroupId"
//report."B1".value = "ArtifactId"
//report."C1".value = "Developers"
//report."D1".value = "Blocker"
//report."E1".value = "Critical"
//report."F1".value = "Major"

def report = xls.INFORMATION
int i=2
def mvn
sf.ids.softwares.each(){ key, soft ->
  
  try{
    mvn = soft.project
  }catch(e){
	println e
  }
  
  if( mvn ){
    report."A$i".value = mvn.groupId
    report."B$i".value = mvn.simpleArtifactId
    report."C$i".value = mvn.version
    report."D$i".value = mvn.scm.isHeadReleased()
    report."E$i".value = "${mvn.developers}"
    report."F$i".value = mvn.scm.devURL
    report."G$i".value = mvn.os
    
  }else{
    report."B$i".value = key

  }
      
  i++
}



xls.write(new FileOutputStream("target/PROJECT-INFORMATION-REPORT.xls"))