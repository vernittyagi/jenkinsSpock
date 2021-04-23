def call(Map variables = [:]){
        node("test"){
                stage("printline"){
                  if(variables.printline){
                        echo("this is print by the pipeline")                                            
                  }
                }
                stage("shStep"){
                  if(variables.shStep){
                        sh 'free -mh'
                  }
                }
        }
}
