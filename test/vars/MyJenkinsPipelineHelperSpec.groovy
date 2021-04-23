import com.homeaway.devtools.jenkins.testing.JenkinsPipelineSpecification
public class MyJenkinsPipelineHelperSpec extends JenkinsPipelineSpecification {
def myJenkinsPipelineHelper = null
def setup(){
	myJenkinsPipelineHelper = loadPipelineScriptForTest("vars/myJenkinsPipelineHelper.groovy")
}
	def "printNode" () {
		when:	
			myJenkinsPipelineHelper name: "Bangalore", lang:"groovy"
		then:
			1 * getPipelineMock( "echo" )("this node has label Bangalore") 
			1 * getPipelineMock( "echo" )("Bangalore node is offline")                                                  
			1 * getPipelineMock("echo")("test for groovy script")
			1 * getPipelineMock("sh")([returnStdout: true, script: "echo hi"]) >> "hi"
	} 

	def "single-argument capture" () {
		when:
			getPipelineMock("echo")("hello")
		then:
			1 * getPipelineMock("echo")(_) >> { arguments ->
			assert "hello" == arguments[0]
		}
}
	def "docker hello-world working"(){
		when: 
			getPipelineMock("sh")('docker run hello-world')
		then:
			1 * getPipelineMock("sh")('docker run hello-world')
}
}
