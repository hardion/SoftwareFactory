package fr.soleil.software.scm.model.script

import com.google.common.io.Files

import fr.soleil.software.model.SoftwareFactory
import fr.soleil.software.model.DeleteType
import fr.soleil.software.scm.model.ScmBuilder

def sf = new SoftwareFactory()
def testname = "ScmTest"
def root=":ext:maven@ganymede.synchrotron-soleil.fr:/usr/local/CVS"
def module="ContinuousIntegration/test/$testname"

File tmp = Files.createTempDir()
tmp.deleteOnExit()

def project = new File(tmp, testname)
project.mkdir()
new File(project, "toto").text="TOTO"
def dir1 = new File(project, "dir1")
dir1.mkdir()
new File(dir1, "titi").text="TITI"
new File(dir1, "tutu").text="TUTU"



sf.addToSCM(root, module, project )
def dir = "/tmp/1329131127254-0" as File
dir.delete()
dir.mkdirs()

println "cvs -d :ext:maven@ganymede.synchrotron-soleil.fr:/usr/local/CVS checkout -N  ContinuousIntegration/test/ScmTest".execute(null, dir).text
println "cvs -d :ext:maven@ganymede.synchrotron-soleil.fr:/usr/local/CVS rtag -F OBSOLETE ContinuousIntegration/test/ScmTest".execute(null, dir).text
def codir = new File(dir,"ContinuousIntegration/test/ScmTest")
println "cvs -d :ext:maven@ganymede.synchrotron-soleil.fr:/usr/local/CVS remove".execute(null, codir).text
println "cvs -d :ext:maven@ganymede.synchrotron-soleil.fr:/usr/local/CVS -z3 -f commit -m test".execute(null, codir).text
println "cvs -d :ext:maven@ganymede.synchrotron-soleil.fr:/usr/local/CVS -z3 -f add OBSOLETE".execute(null, codir).text

//def scm = ScmBuilder.create(root, module)
//sf.deleteFromSCM(["scm":scm], DeleteType.CLOSE, "TEST SCM CREATE/DELETE OK")
