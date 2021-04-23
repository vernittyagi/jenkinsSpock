import com.homeaway.devtools.jenkins.testing.JenkinsPipelineSpecification

class  MySamplePipelineSpec extends JenkinsPipelineSpecification{
        def mySamplePipeline = null

        def setup(){
        mySamplePipeline = loadPipelineScriptForTest("vars/mySamplePipeline.groovy")                                                  
        }

        def "[mySamplePipeline] will print  if printline is true"() {
        when:
            mySamplePipeline  printline: true
        then:
            1 * getPipelineMock("echo")("this is print by the pipeline")
        }

        def "[mySamplePipeline] will sh if shStep is true"() {
        when:
            mySamplePipeline  shStep: true
        then:
            1 * getPipelineMock("sh")("free -mh")
        }
}
