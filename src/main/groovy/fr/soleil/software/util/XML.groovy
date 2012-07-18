package fr.soleil.software.util

class XML{

  static def replace(def text, def pattern, int level, def closure){
    int l=0
    def result=[]
    text.eachLine{ line ->
      (line =~ /<\w+[^>]*>/).each{ l++ }
      (line =~ /<\/\w+[^>]*>/).each{ l-- }

      if( level!=-1 && level==l ){
        result << line.replaceAll(pattern ,closure)
      }else{
        result << line
      }
    }

    return result.collect{ it }.join("\n")

  }
}