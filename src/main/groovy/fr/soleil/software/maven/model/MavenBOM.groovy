package fr.soleil.software.maven.model

import org.slf4j.*  
import com.google.common.io.Files

/**
 * Utility for BOM Maven project
 */
class MavenBOM extends MavenProject{

  Logger log = LoggerFactory.getLogger(MavenBOM.class)

  def MavenBOM(def scm){
    super(scm)
  }
  
  def removeDependency(MavenProject mvn){
    def tmp = Files.createTempDir()
    tmp.deleteOnExit()

    def model // THE TRUE MODEL from Maven API
    def pomFilename = scm.checkout("pom.xml","",tmp.absolutePath) // pom.xml file of this BOM from SCM
    // Open it and read it from Maven API
    new File(pomFilename).withReader{ reader ->
      model = MavenProject.modelReader.read( reader)
      // close automatically at scope end
    }
    if(model != null){
      // check dependency
      def dependency = model.dependencyManagement.dependencies.find(){ 
        it.artifactId == mvn.artifactId &&
        it.groupId == mvn.groupId
      }
      
      if(dependency){
        // 1. Remove from dependencyManagement
        model.dependencyManagement.removeDependency(dependency)

        //2. Remove from property in release and continuous-integration profile
        model.profiles.each{
          if(it.id == "continuous-integration" || it.id == "release"){
            it.properties.remove(this.getVersionProperty(mvn))
          }
        }
        // 3. Write xml file
        new File(pomFilename).withWriter{ writer ->
          MavenProject.modelWriter.write(writer,model)
        }
        // 4. Commit file
        scm.commit(pomFilename, "Dependency DELETED : ${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
      }
    }
  }

  def updateDependency(MavenProject mvn){
    def tmp = Files.createTempDir()
    tmp.deleteOnExit()

    def model // THE TRUE MODEL from Maven API
    def pomFilename = scm.checkout("pom.xml","",tmp.absolutePath) // pom.xml file of this BOM from SCM
    // Open it and read it from Maven API
    new File(pomFilename).withReader{ reader ->
      model = MavenProject.modelReader.read( reader)
      // close automatically at scope end
    }
    if(model != null){
      // check dependency
      def exist = false
      for (dependency in model.dependencyManagement.dependencies){
        if(dependency.artifactId == mvn.project.artifactId.text()){
          exist = true
          break
        }
      }
      if(exist){
        log.info("This project is already added to this bom")
      }else{
        // update dependencyManagement section
        def dep = new org.apache.maven.model.Dependency() // Force class loading
        dep.groupId = mvn.project.groupId.text()
        dep.artifactId = mvn.project.artifactId.text()
        dep.version = this.getProperty4VersionProperty(mvn)
        model.dependencyManagement.addDependency(dep)
        // Update profiles with corresponding property
        model.profiles.each{
          if(it.id == "continuous-integration" || it.id == "release"){
            it.properties.setProperty(this.getVersionProperty(mvn), mvn.project.version.text())
          }
        }
        // write xml file
        new File(pomFilename).withWriter{ writer ->
          MavenProject.modelWriter.write(writer,model)
        }
        // commit file
        scm.commit(pomFilename, "New project updater : ${dep.groupId}:${dep.artifactId}:${dep.version}")
      }
    }
  }

  def getVersionProperty(mvn){
    return "${mvn.simpleArtifactId}.version"
  }
  def getProperty4VersionProperty(mvn){
    return "\${${this.getVersionProperty(mvn)}}"
  }

}
