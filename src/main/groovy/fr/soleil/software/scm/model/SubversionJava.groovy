package fr.soleil.software.scm.model

import org.slf4j.LoggerFactory
import org.slf4j.Logger

import org.tmatesoft.svn.core.*
import org.tmatesoft.svn.core.wc.*
import org.tmatesoft.svn.core.wc.SVNWCUtil

import fr.soleil.software.util.XML

/**
 * Subversion implementation
 * 
 * 
 * the root attribute corresponds to url
 * the module isn't used
 * 
 */
class SubversionJava extends SCM{

  Logger log = LoggerFactory.getLogger(SubversionJava.class)  
  
  static ttb = [trunk:"trunk", tags:"tags", branches:"branches"]
  static{
    org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory.setup();
    org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl.setup();
    org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory.setup();

  }
  
  def versionPattern = /([0-9]+).([0-9]+).([0-9]+)/
  def newReleasePattern = /-([0-9]+)\.([0-9]+)\.([0-9]+)/
  def legacyReleasePattern = /release_([0-9]+)_([0-9]+)_([0-9]+)/
  
  def urlPattern = /(^http.*?)(trunk|tags|branches)(.*$)/

  def svn
  def url

  def SubversionJava(def base, def module="",def tag=""){
    //def url = SVNURL.parseURIEncoded("$root/$where/$file")
    // Recompose url with module
    url = (base + (module?"/$module":"")).replaceAll("(?<!:)//","/")
    
    // but Decompose svn url in "$root/$ttb/$module"
    // Retrieve all component by url
    def m = (url =~ urlPattern)
    if(m.matches()){
      this.root = m[0][1]
      this.branch = m[0][2]?:ttb.trunk
      if(this.branch != ttb.tags){
        this.module = m[0][3]?:""
      }else{
        this.tag = m[0][3]?:""
      }
      
    }else{
      throw new Exception("The svn url doesn't match $urlPattern")
    }

    if(forceRealms){
      forceRealms()
    }
    
    // Init log
    def handler = [
      checkCancelled:{
      },
      handleEvent:{ event, progress ->
        log.debug("$progress, $event (${event.errorMessage} ${event.contentsStatus})")
      }]
    def options = SVNWCUtil.createDefaultOptions(true)
    def authentificationManager = SVNWCUtil.createDefaultAuthenticationManager()
    svn = org.tmatesoft.svn.core.wc.SVNClientManager.newInstance(options, authentificationManager)
    
    svn.eventHandler = handler as ISVNEventHandler
  }
  
  SCM.ScmType getProvider(){
    return ScmType.svn
  }

  String getUserURL(){
    (this.url =~/https:/).replaceFirst("http:").replaceAll("(?<!:)//","/")
  }
  
  String getDevURL(){
    (this.url =~/http:/).replaceFirst("https:").replaceAll("(?<!:)//","/")
  }
  
  String getViewURL(){
    this.userURL
  }
  
  /**
   * This method has two responsabilities
   * TODO : refactoring to have a single responsability
   * 
   * "svn checkout $root/$where/$file $coDir"
   */
  def checkout(def file, def tag="", def coDir=""){
    def result
    log.debug("Checkout $file with tag $tag and dir $coDir")
    def myUrl = ""
    if(tag){
      myUrl = "$root${ttb.tags}/$tag"
    }else if(this.tag){
      myUrl = "$root$branch$tag"
    }else{
      myUrl = "$root$branch$module"      
    }
    
     
    // Remove old file if local co
    if(coDir != "" ){
      new File("${coDir}").deleteDir()
      
      // SVN only support checkout of directory
      def svnurl = SVNURL.parseURIEncoded("$myUrl")
      svn.updateClient.doCheckout(svnurl, coDir as File, SVNRevision.HEAD, SVNRevision.HEAD, true)

      // Return filename instead of file content
      result = "${coDir}/${file}"
    }else{
      // simply return the content of the file
      result = new URL("$myUrl/$file").text
    }
    
    return result
  }

  def commit(def filename, def message="From Hudson"){
    //"svn commit ${filename} --file ${tmp.absolutePath}"
    def info = svn.commitClient.doCommit([filename as File] as File[],true, message,null,null,false,false,SVNDepth.INFINITY)
    info.each(){ log.debug("$it") }
  }

  /**
   * Made only a copy from trunk to tag 
   * local file isn't used
   */
  def tag(def file, def tag, def move=false){
    // "svn copy $root/trunk $root/tags/$tag  -m \"Remote copy.\"
    def srcUrl = SVNURL.parseURIEncoded("$root$branch$module/$file")
    def destUrl = SVNURL.parseURIEncoded("$root${ttb.tags}/$tag")
    svn.copyClient.doCopy(srcUrl,SVNRevision.HEAD, destUrl,false, !move, "Remote copy.")
  }
  
  /**
   * Declare that a project is obsolete or has moved to another SCM
   * Often it means :
   * - erase all files from trunk
   * - add file where the content of the file can indicates the new repository or else
   * 
   * @param filename Name of the file to add after removing all file (README, MOVE_TO, OBSOLETE ...)
   */
  def close(def filename, def message ){
    // 1. Move sources to the obsolete tag branch
    def srcUrl = SVNURL.parseURIEncoded("$root${ttb.trunk}/$module")
    def destUrl = SVNURL.parseURIEncoded("$root${ttb.tags}/OBSOLETE/$module")
    svn.copyClient.doCopy(srcUrl,SVNRevision.HEAD, destUrl,true, true, "Close module")
    
    // 2. Add a file MOVETO to the root of the module
    File tmp = File.createTempFile(filename,"")
    tmp.deleteOnExit()
    tmp.text = message
    def dstUrl = SVNURL.parseURIEncoded("$root${ttb.trunk}/$module")
    svn.commitClient.doImport(tmp,dstUrl,"Close project : $message", null, false, false,SVNDepth.IMMEDIATES)

  }

  /**
   * Import new file to the repository
   * 
   * @param src Local source file to import (Directory or File)
   * @param target Target file pointing to a directory or a file
   * @param message Historic message
   */
  def importFile(File src, String target, String message ){

    def dstUrl = SVNURL.parseURIEncoded("$root$branch$module/$target")

    try {
      svn.commitClient.doImport(src as File, dstUrl, "New Import : $message", null, false, false,SVNDepth.INFINITY)
    } catch (SVNException e) {
      if (e.getErrorMessage() == null) {
        throw e;
      }
      if(e.errorMessage.errorCode == SVNErrorCode.RA_DAV_ALREADY_EXISTS ){
        log.warn("Break import process cause : "+e.errorMessage.message)
      }else{
        log.error("Unknown error while import source : ${e.errorMessage.errorCode} ${e.errorMessage.message}")
      }
    }
  }
  
  /**
   * Add file to ignore (only from root)
   * 
   */
  def addToIgnore(String filename){
    def dstUrl = SVNURL.parseURIEncoded("$root$branch$module")

    // Retrieve the old value
    def prop = svn.getWCClient().doGetProperty(dstUrl, SVNProperty.IGNORE,  SVNRevision.HEAD,  SVNRevision.HEAD)
    def value = filename
    if(prop?.value){
      value = "${prop.value}\n${value}"
    }
    // Set the new one
    svn.getWCClient().doSetProperty(dstUrl, SVNProperty.IGNORE, SVNPropertyValue.create(value), SVNRevision.HEAD, "Added $filename to ignore", null, false, null)
  }
  
  /**
   * Export file from the repository
   * 
   * @param target Target directory
   */
  def exportFile(File target, def tag=""){
    def myUrl = ""
    if(tag){
      myUrl = "$root${ttb.tags}/$tag"
    }else if(this.tag){
      myUrl = "$root$branch$tag"
    }else{
      myUrl = "$root$branch$module"      
    }
    
    // SVN only support checkout of directory
    def svnurl = SVNURL.parseURIEncoded("$myUrl")
    def result = svn.updateClient.doExport(svnurl, target as File, SVNRevision.UNDEFINED, SVNRevision.HEAD, null, true, SVNDepth.INFINITY)
    log.debug("SVN Export to $target.absolutePath from revision $result")
  }

  
  def release(String version, String basedir="", Closure updateSnapshot){

    // Retrieve current revision on the trunk
    def rev= this.getLastRevision("trunk")
    
    // Call some stuff (maven use it to update in snapshot version)
    updateSnapshot.call()
    
    // Tag the previous revision
    def srcUrl = SVNURL.parseURIEncoded("$root$branch$module")
    def destUrl = SVNURL.parseURIEncoded("$root${ttb.tags}/${this.project}-${version}")
    svn.copyClient.doCopy(srcUrl,SVNRevision.create(rev), destUrl,false, true, "Remote copy.")
    //    doCopy(SVNURL srcURL, SVNRevision srcRevision, SVNURL dstURL, boolean isMove, boolean failWhenDstExists, String commitMessage) throws SVNException {
 
  }
  
  def getLastRevision(def path){
    def revision=-1
    def url = SVNURL.parseURIEncoded(root)
    def printHandler = {dirEntry ->
      if(dirEntry.name==path){
        revision=dirEntry.revision
      }
    } as ISVNDirEntryHandler
    
    svn.logClient.doList(url,SVNRevision.HEAD,SVNRevision.HEAD,false,SVNDepth.IMMEDIATES,SVNDirEntry.DIRENT_ALL, printHandler)
  
    return revision
  }

  def isHeadReleased(def file="pom.xml"){
	def result = false
	def revTrunk = this.getLastRevision( (module ? "trunk/$module/$file" : "trunk/$file") )
	def lastReleaseTag = this.getLastReleaseTag(file)
	if(lastReleaseTag){
		result = this.getLastRevision("tags/$lastReleaseTag/$file") > revTrunk
	}
    
    return result
  }

  def getLastReleaseTag(def file){
    def result = ""
    def revision = 0
    def list=[]
    
    def handler = { dirEntry -> list.add dirEntry } as ISVNDirEntryHandler
    def url = SVNURL.parseURIEncoded(root+"tags")
    try{
      svn.logClient.doList(url,SVNRevision.HEAD,SVNRevision.HEAD,false,SVNDepth.IMMEDIATES,SVNDirEntry.DIRENT_ALL, handler)

      list.each(){
        if( it.revision > revision && (it.name ==~ legacyReleasePattern || it.name ==~ (/${this.project}/+newReleasePattern) ) ){
          revision = it.revision
          result = it.name
        }
      }
      
    }catch(Exception e){
      log.error("Something goes wrong $e")
      result=""
    }
    return result
  }

  def getLastVersionTag(def file){
    return getLastReleaseTag(file)?.replaceAll(/.*/ + versionPattern){ full, X, Y, Z -> "$X.$Y.$Z"}
  }
    
  def getExecutable(){ "" }
  
  public void forceRealms(){
    // Brute force
    root = root.replaceFirst("http://","https://")
  }
  
  def switchBranch(def b, boolean isTag){
    def oldUrl = this.url
    
    if(b == ttb.trunk){
      this.branch = ttb.trunk
      this.url = root+branch+module
    }else{
      this.branch = isTag ? ttb.tags : ttb.branches
      this.tag = b
      if(this.branch == ttb.tags){
        this.url = "$root/$branch/$tag" //bug 19896 il manque pê : +"/"+module
      }else{
        this.url = "$root/$branch/$tag/$module"
      }
    }
    return oldUrl != this.url
  }
  
  def isTag(){
    branch==ttb.tags
  }

  String toString(){
    super.toString()+ " url:$url"
  }


}
