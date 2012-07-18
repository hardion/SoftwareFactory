package fr.soleil.software.scm.model

import org.slf4j.LoggerFactory
import org.slf4j.Logger
  
import fr.soleil.software.scm.model.SCM.ScmType

/**
 * Build an scm object
 * Only compatible with CVS and Subversion
 *
 */
class ScmBuilder {
  
  static Logger log = LoggerFactory.getLogger(ScmBuilder.class)

  static SCM create(def root, def module="", def tag=""){
    
    def result
    if(root.startsWith(":")){
      // CVS :pserver or :ext
      result = ScmBuilder.create(ScmType.cvs, root, module, tag)
      
    } else if(root.startsWith("http")){
      // we suppose SVN
      result = ScmBuilder.create(ScmType.svn, root, module, tag)
      
    }else{
      log.warn("This SCM isn't supported : $root $module $tag")
    }
    return result
    
  }
  
  static SCM create(ScmType type, def root, def module, def tag=""){
    def result
    switch(type){
      
      case ScmType.cvs :
      result = new CVS(root, module, tag)
      break
      
      case ScmType.svn :
      result = new SubversionJava(root, module, tag)
      break

      case ScmType.git :
      break
      
    }
    return result
  }
}
