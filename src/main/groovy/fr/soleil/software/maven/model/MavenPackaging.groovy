package fr.soleil.software.maven.model

import org.slf4j.*
import com.google.common.io.Files

class MavenPackaging extends MavenProject{
  
  Logger log = LoggerFactory.getLogger(MavenPackaging.class)
  
  // From Maven low-level API, no warranty about upstream compatibility
  //static Class XPP3READER = Class.forName("org.apache.maven.model.io.xpp3.MavenXpp3Reader", true, hudson.model.Hudson.instance.getPluginManager().uberClassLoader)
  //static Class XPP3WRITER = Class.forName("org.apache.maven.model.io.xpp3.MavenXpp3Writer", true, hudson.model.Hudson.instance.getPluginManager().uberClassLoader)

  def MavenPackaging(def scm){
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
      def dependency = model.dependencies.find(){ 
        it.artifactId == mvn.artifactId &&
        it.groupId == mvn.groupId
      }
      
      if(dependency){
        // 1. Remove from dependencyManagement
        model.removeDependency(dependency)

        // 2. Write xml file
        new File(pomFilename).withWriter{ writer ->
          MavenProject.modelWriter.write(writer,model)
        }
        // 3. Commit file
        scm.commit(pomFilename, "Dependency DELETED : ${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
      }
    }
  }


  def addDependency(MavenProject mvn){
    def result=false
    // No release version == no deployment
    if( "" == mvn.scm.getLastVersionTag("pom.xml") ){
      log.warn("No release Tag found. Please check your SCM")
    }else{

      //def modelReader = XPP3READER.newInstance() // Force class loading
      //def modelWriter = XPP3WRITER.newInstance() // Force class loading
      //def modelReader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader()
      //def modelWriter = new org.apache.maven.model.io.xpp3.MavenXpp3Writer()
      def tmp = Files.createTempDir()
      tmp.deleteOnExit()

      def model // THE TRUE MODEL from Maven API
      def pomFilename = scm.checkout("pom.xml","",tmp.absolutePath) // pom.xml file of this BOM from SCM
      // Open it and read it from Maven API
      new File(pomFilename).withReader{ reader ->
        model = modelReader.read( reader)
        // close automatically at scope end
      }

      if(model == null){
        log.error("Something goes wrong with the packaging project : $scm.module")
      }else{

        // check dependency
        def exist = false
        for (dependency in model.dependencies){
          if(dependency.artifactId == mvn.project.artifactId.text()){
            exist = true
            break
          }
        }

        if(exist){
          log.warn("Dependencies already added in $scm.module")
          result=true
        }else{
          // update dependencyManagement section
          def dep = new org.apache.maven.model.Dependency()
          dep.groupId = mvn.project.groupId.text()
          dep.artifactId = mvn.project.artifactId.text()

          // Specific case : Packaging with distrib plugin
          def distribUpdater = {
            // Update profiles with corresponding property
            model.profiles.each{
              if(it.id == "SoleilRoot" || it.id == "DeviceRoot"){
                //it.properties.setProperty(this.getVersionProperty(mvn), mvn.project.version.text())
                //it.dependencies.addDependency(dep)
                log.warn("**** DISTRIB Plugin compatibility : You need to manually update the Packaging project : "+scm.cvsroot+" "+scm.module)
              }
            }
          }
          // Common case : Packaging with assembly plugin
          def assemblyUpdater = {
            log.debug("Add to common dependencies Dependency : ${dep.groupId} ${dep.artifactId}")
            model.dependencies.add(dep)
          }
          def updater = [
                        "fr.soleil.packaging:SoleilRoot":distribUpdater,
                        "fr.soleil.packaging:DeviceRoot-Java":distribUpdater,
                        "fr.soleil.packaging:DeviceRoot-CPP-RHEL4":assemblyUpdater,
                        "fr.soleil.packaging:DeviceRoot-CPP-WIN32":assemblyUpdater,
          ]

          // instanceimport Update
          updater[model.groupId+":"+model.artifactId]()

          // write xml file
          new File(pomFilename).withWriter{ writer ->
            modelWriter.write(writer,model)
          }
          // commit file
          scm.commit(pomFilename, "New project updater : ${dep.groupId}:${dep.artifactId}:${dep.version}")
          result=true
        }
      }
    }
    return result
  }
}
