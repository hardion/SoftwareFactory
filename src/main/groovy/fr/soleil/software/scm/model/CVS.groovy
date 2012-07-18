package fr.soleil.software.scm.model

import org.slf4j.LoggerFactory
import org.slf4j.Logger
import com.google.common.io.Files

import static fr.soleil.software.scm.model.SCM.ScmType

/**
 * Utility for SCM - CVS implementation
 */
class CVS extends SCM{

  Logger log = LoggerFactory.getLogger(CVS.class)

  def CVS(def root, def module="", def branch=""){
    this.root = root
    if(!module){
      // The module is contains in the root
      def matches = (this.root =~ /(.*):(.*)$/)
      this.root = matches[0][1]
      this.module = matches[0][2]
    }else{
      this.module = module      
    }
    this.branch = branch
    this.tag = this.branch?this.branch:""
    
    if(forceRealms){
      forceRealms()
    }
  }
  
  def getExecutable(){ "cvs" }

  SCM.ScmType getProvider(){
    return ScmType.cvs
  }
  
  String getUserURL(){
    (root =~/:ext:.*@/).replaceFirst(":pserver:anonymous@")+":"+this.module
  }
  
  String getDevURL(){
    (root =~ /:pserver:anonymous@/).replaceFirst(":ext:@")+":"+this.module
  }
  
  String getViewURL(){
    def host = this.root.split(":")[2]
    host = host.contains("@") ? host.split("@")[1] : host
    def result = ""
    switch (host){
    case "ganymede" :
      result = "http://$host/viewcvs/viewcvs.cgi/${this.module}"
      break;
    case ~/.*tango-ds.*/ :
      result = "http://$host/viewvc/tango-ds/${this.module}"
      break;
    }
    return result
  }
  
  def checkout(def file, def stag="", def coDir=null){
    // Add rtag if checkout from a specific tag
    def path = module + (file ? "/${file}" : "") // if file add separator
    stag = (stag?:tag) // get tag from constructor by default
    def rtag = stag ? "-r ${stag}" : ""
    def output = (coDir ? "-N" : "-p")
        
    // Remove old file if local co
    def current
    if( coDir ){
      new File("${coDir}/${path}").deleteDir()
      current = new File(coDir)
      current.mkdirs()
    }

    def result = cvs(""" -d ${root} checkout ${output} ${rtag} ${path}""",current)

    // Return filename instead of file content
    if( coDir ){
      result = "${coDir}/${path}"
    }
        
    return result
  }


  def commit(def filename, def message="From Hudson"){
    File tmp = File.createTempFile("commitmessage",".dat")
    tmp.deleteOnExit()
    tmp.text = message
    log.debug("Commit file ${filename} with message ${tmp.text} from file ${tmp.absolutePath}")
    def file = filename as File
    if(file.directory ){
      cvs(""" -d ${root} -z3 -f commit -F ${tmp.absolutePath}""", file)
    }else{
      cvs(""" -d ${root} -z3 -f commit -F ${tmp.absolutePath} $file.name""", file.parentFile)
    }
  }

  def tag(def file, def tag, def move=false){
    def options= move? "-F":""
    if(file){
      log.debug("tag -d ${root} tag ${options} ${tag} ${file}")
      cvs(""" -d ${root} tag ${options} ${tag} ${file}""")
    }else{
      log.debug("rtag -d ${root} tag ${options} ${tag} ${file}")
      cvs(""" -d ${root} rtag ${options} ${tag} ${module}""")
    }
  }
  
  def release(String version, String basedir="", Closure updateSnapshot){
    this.tag(basedir, "release_${version.replace('.','_')}", false)
    // updateSnapshot not call otherwise it breaks the isHeadReleased() => always false
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
    // Preparation
    File tmp = Files.createTempDir()
    tmp.deleteOnExit()
    def workspace = this.checkout(null,null,tmp.absolutePath)
    def ws = new File(workspace)

    // 1. TAG last sources
    this.tag(null,"OBSOLETE",true)
    
    // 2. remove all files
    ws.eachFileRecurse(){
      if(!it.directory && it.parentFile.name != "CVS"){
        it.delete()
      }
    }
    cvs("""-d ${root} remove""",ws)
    this.commit(workspace, "Close Project")
    
    // 3. Add a new readme file MOVETO to the root of the module
    def readme = new File(ws,filename)
    readme.createNewFile()
    readme.text = message
    
    cvs("""-d ${root} add ${readme.name}""",ws)
    
    // 4. commit
    this.commit(workspace, "Close Project")
  }

  /**
   * Import new file to the repository
   * 
   * @param src Local source file to import (Directory or File)
   * @param target Target file pointing to a directory or a file
   * @param message Historic message
   */
  def importFile(File src, String target, String message ){
    message = message.replaceAll( ~/[ $,.:;@\/]/, "_" )
    def output = cvs(""" -d ${root} import -m $message ${module}/$target softwarefactory initial""", src)
    log.debug("""cvs import : \n $output""")
  }
  
  def addToIgnore(String filename){
    // Preparation
    File tmp = Files.createTempDir()
    tmp.deleteOnExit()
    def workspace = this.checkout(null,null,tmp.absolutePath)
    def ws = new File(workspace)
    
    File ignore = new File(ws, ".cvsignore")
    if(!ignore.exists()){
      ignore.createNewFile()
      cvs("""-d ${root} add ${ignore.name}""",ws)
    }
    
    if( ! ignore.text.contains(filename) ){
      ignore << filename + System.getProperty("line.separator")
      this.commit(workspace, "Add $filename to .cvsignore")
    }else{
      log.warn("Already ignored ($filename)")
    }
  }

  
  /**
   * Export file from the repository
   * 
   * @param target Target directory
   */
  def exportFile(File target, def tag=""){
    def option = (tag ? "-r$tag" : "-DNOW")
    def output = cvs(""" -d ${root} export $option ${module}""", target)
    log.debug("""cvs export : \n $output""")
    // Return filename instead of file content
    return "$target.absolutePath/$module"
  }
  
  def isHeadReleased(def file="pom.xml"){
    def result = false
	def lastReleaseTag = getLastReleaseTag(file)
	if(lastReleaseTag){
		result = ( cvs("-d ${root} rdiff -s -r ${lastReleaseTag}  ${module}") == "" )
	}
    return result
  }

  def getLastReleaseTag(def file){
    def result = getLastVersionTag(file)

    if(result != ""){
      result = "release_"+result.replace(".","_")
    }
    return result
  }

  def getLastVersionTag(def file){
    def result = ""
    def cvslog = cvs("-d ${root} rlog -h ${module}/${file}") // Create the String
    def version = cvslog =~ /release_(.*):/
    if(version.find()){
      result = version[0][1].replace("_",".")
    }else{
      log.debug("No tag for ${root} ${module} ${file} : ${cvslog}")
    }

    return result
  }
  
  def switchBranch(def branch, boolean tag){
    def oldBranch = this.branch
    if(branch == "HEAD"){
      this.branch = ""
      this.tag = false
    }else{
      this.branch = branch
      this.tag = tag
    }
    return oldBranch != this.branch
  }
  
  def isTag(){
    this.tag != ""
  }



  private def cvs(def commandline, def file=null, def retry=1){
    
    String result = ""
    def cmd = Command.execute("cvs ${commandline}", file)
    
    if(cmd.sout!=""){ // Result OK
      result = cmd.sout

    } else if(cmd.serr!=""){ //Result OK
      log.warn("Last command cvs ${commandline} return error : $cmd.serr")

    }else if(retry--){
      log.warn("Last command return nothing : retry ($retry) cvs ${commandline}")
      result = this.cvs(commandline, file, retry)
    }else{
      log.error("Can't correctly execute : cvs ${commandline}")
    }
    
    return result
  }
  
  public void forceRealms(){
    // Brute force
    root = root.replaceFirst(":pserver:anonymous:@ganymede",":ext:maven@ganymede")
    .replaceFirst(":pserver:anonymous@ganymede",":ext:maven@ganymede")
    .replaceFirst(":pserver:anonymous:@tango-ds",":ext:qa-soleil@tango-ds")
    .replaceFirst(":pserver:anonymous@tango-ds",":ext:qa-@tango-ds")
  }
  
  private String toAnonymous(def root){
    (root =~/:ext:.*@/).replaceFirst(":pserver:anonymous@")
  }
}

class Command {

  Logger log = LoggerFactory.getLogger(Command.class)
  
  static Command execute(def commandline, def dir=null){
    def result = new Command()
    result.cmd(commandline, dir)
    return result
  }
  
  def sout = new StringBuffer()
  def serr = new StringBuffer()

  private def waitForProcessOutput(Process self, StringBuffer output, StringBuffer error) {

    Thread tout = consumeProcessOutputStream(self, output);
    Thread terr = consumeProcessErrorStream(self, error);

    try { tout.join(); } catch (InterruptedException ignore) {}

    try { terr.join(); } catch (InterruptedException ignore) {}

    try { self.waitFor(); } catch (InterruptedException ignore) {}

  }
  private def Thread consumeProcessOutputStream(Process self, StringBuffer output) {

    Thread thread = new Thread(new TextDumper(self.getInputStream(), output));
    thread.start();

    return thread;

  }
  private def Thread  consumeProcessErrorStream(Process self, StringBuffer err) {

    Thread thread = new Thread(new TextDumper(self.getErrorStream(), err));
    thread.start();

    return thread;

  }
  /* END HACK from Groovy 1.6.5 */
  private def cmd(def commandline, def dir=null){
    log.debug("Last command from current dir $dir : ${commandline}")
    
    def proc = "${commandline}".execute(null, dir)

    waitForProcessOutput(proc,sout, serr)

    // Obtain status and output
    if( proc.exitValue() ){
      log.error( "command  \"${commandline}\" failed")
      log.error( 'out:\n' + sout)
      log.error( 'err:\n' + serr)
      throw new Exception("command  \"${commandline}\" failed : "+serr.toString())
    }

    return sout.toString()
  }

}


/* HACK from Groovy 1.7.3
 * Because Hudson is only 1.6.0 compliant ... */
class TextDumper implements Runnable {
  InputStream inputstream;
  StringBuffer sb;
  Writer w;

  public TextDumper(InputStream inputstream, StringBuffer sb) {
    this.inputstream = inputstream;
    this.sb = sb;
  }

  public TextDumper(InputStream inputstream, Writer w) {
    this.inputstream = inputstream;
    this.w = w;
  }

  public void run() {
    InputStreamReader isr = new InputStreamReader(inputstream);
    BufferedReader br = new BufferedReader(isr);
    String next;
    try {
      while ((next = br.readLine()) != null) {
        if (sb != null) {
          sb.append(next);
          sb.append("\n");
        } else {
          w.write(next);
          w.write("\n");
        }
      }
    } catch (IOException e) {
      throw new GroovyRuntimeException("exception while reading process stream", e);
    }
  }
}


