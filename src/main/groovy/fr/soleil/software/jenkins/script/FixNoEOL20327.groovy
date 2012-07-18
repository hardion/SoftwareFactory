package fr.soleil.software.jenkins.script

import fr.soleil.software.jenkins.model.JenkinsREST

import fr.soleil.software.scm.model.ScmBuilder
import fr.soleil.software.scm.model.SCM
import fr.soleil.software.maven.model.MavenProject

import org.slf4j.*

System.setProperty("http.proxyHost","195.221.0.6")
System.setProperty("http.proxyPort","8080")
System.setProperty("https.proxyHost","195.221.0.6")
System.setProperty("https.proxyPort","8080")
System.setProperty("http.nonProxyHosts","*.ica|*.synchrotron-soleil.fr|calypso|ganymede|controle")

org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory.setup();
org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl.setup();
org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory.setup();

SCM.forceRealms = true

jenkins = new JenkinsREST()

["NI660Xsl", "ds_DCCTBergoz", "ds_KepcoAnalogControl"].each{
  fixNOEOL(it)
}

/**
 * FIX the bug 20327
 */
def fixNOEOL(String jobname) {

  boolean changed=false

  def job= jenkins.getJob(jobname)
  def scm = jenkins.scmFrom(job)

  new MavenProject(scm).change(){
    
    def description = "20327 : Vera++ Compliance (no more noeol)"
    def tmp = scm.checkout("","","bug20327")

    // Analyse each file with noeol
    new File(tmp).eachFileRecurse(){file -> 
      if(file.isFile() && file.name ==~ /.*\.((cpp)|(h))/ ){
        def lastLine = file.text[-1]
        if(! ["\r","\n","\r\n"].any{it==lastLine} ){
          file.text += org.codehaus.groovy.tools.Utilities.eol()
          println file.name+" has been changed"
          changed=true
        }
      }
    }

    // Commit changes
    if(changed){
      scm.commit(tmp, description)
    }
    return description
  }
  
}
