def call(Map variables=[:]){
	echo("this node has label $variables.name")
	echo("$variables.name node is offline")	
	echo("test for $variables.lang script")                                                           
	sh([returnStdout: true, script: "echo hi"])
	echo("hello")
	sh 'docker run hello-world'
}
