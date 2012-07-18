package fr.soleil.software.scm.model


abstract class SCM {
  
  public enum ScmType {cvs,svn,git}

  static boolean forceRealms=false
  
  abstract ScmType getProvider()

  abstract String getUserURL()
  
  abstract String getDevURL()
  
  abstract String getViewURL()
  
  abstract def checkout(def file, def tag="", def coDir="")

  //TODO in replacement of checkout(def file, def tag="")
  //abstract def checkoutAsStream(def file, def tag="")
  
  abstract def commit(def filename, def message="From Hudson")

  abstract def tag(def file, def tag, def move=false)
  
  /**
   * Declare that a project is obsolete or has moved to another SCM
   * Often it means :
   * - erase all files from trunk
   * - add file where the content of the file can indicates the new repository or else
   * 
   * @param filename Name of the file to add after removing all file (README, closeSCM, OBSOLETE ...)
   * @param message The content of the file defined by the filename parameter
   * 
   */
  abstract def close(def filename, def message )
  
  /**
   * Import new file to the repository
   * 
   * @param src Local source file to import (Directory or File)
   * @param target Target file pointing to a directory or a file
   * @param message Historic message
   */
  abstract def importFile(File src, String target, String message )
  
  /**
   * Export file from the repository
   * 
   * @param target Target directory
   */
  abstract def exportFile(File target, def tag )

  abstract def isHeadReleased(def file="pom.xml")

  abstract def getLastReleaseTag(def file)

  abstract def getLastVersionTag(def file)
    
  abstract def getExecutable()
  
  // completly specific to the migration process
  // refactoring if you want to enlarge uses
  abstract def release(String version, String basedir="",  Closure updateSnapshot)
  
  abstract protected void forceRealms()

  abstract def switchBranch(def branch, boolean tag)
  
  abstract def isTag()
  def isLastReleaseTag(){
    if(this.isTag()){
      
    }
  }
  
  abstract def addToIgnore(String filename)

  
  String toString(){
        "Scm : root:$root module:$module branch:$branch tag:$tag"
  }
  
  def samePlace(SCM other){
    return (other.root == this.root) && (other.module == this.module)
  }
  
  def getScmKey(){
    this.getDevURL()
  }
    
  String root
  String module
  String branch
  String tag
  String project // use to scan release tag with the name of maven project
}


