import groovy.transform.Field
import java.util.concurrent.*
import jenkins.*
import jenkins.model.*
import hudson.*
import hudson.model.*

@Field

def cloudMapping = [
        DHN78: "10.133.165.134",
        DHN88: "10.131.211.117",
        DHN40: "10.131.225.105",
        DHN55: "10.131.68.69",
        DHN100: "10.175.0.165",
        OHN70: "10.133.165.134",
        GSN103: "10.131.225.105"
]
@Field
def errorVms=[]
@Field cloudName;
@Field flavor="AT";
@Field csl="fnmst001";
@Field gitBranch="master"
@Field username="fnmst001"
@Field password="fnmst001"
@Field ip_log;
@Field gitRepo="brewery_IAAC"
@Field repoPath="/home/fnmst001/ansible/"
@Field name;
@Field type;
@Field description;

@Field
def matchedResources;
@Field semaphoreThreadCountAT=10
@Field semaphoreThreadCountSlave=2
@Field
Semaphore semaphore
@Field init
@Field threadCount
@Field
def countArr = []
@Field
def blocked_list=[]
@Field
def position = 0
@Field
def flag = true
@Field
def endPoint = 0
@Field
def waiting = false


def call(Map parameters = [:]) {
    try {
        if (parameters["type"].equals("AT")) {
           setThreadCount(semaphoreThreadCountAT)
        }
        else
        {
            setThreadCount(semaphoreThreadCountSlave)
        }
        semaphore = new Semaphore(semaphoreThreadCount, true);
        scriptInstance = parameters["script"]
        if (parameters.containsKey("cloudName")) {
            cloudName = parameters['cloudName']
        }
        osVersion = "18.04"
        if (parameters.containsKey("flavor")) {
            flavor = parameters["flavor"]
        }
        if (parameters.containsKey("csl")) {
            csl = parameters["csl"]
        }
        if (parameters.containsKey("type")) {
            type = parameters["type"]
        }
        if (parameters['pipelineIAACBuild'] == false) {
            gitBranch = "master-MSEE-6586-toTestIAAC-Testing-Automation-vtyagi"
        }
        arrIndex = 0
        if (parameters["type"].equals("AT")) {
            label = parameters["label"]
            matchedResources = parameters['matchedResources']
            pullLatest = sh(
                    script: 'sshpass -p' + password + ' ssh -o StrictHostKeyChecking=no ' + username + '@' + cloudMapping[cloudName] + ' "cd ' + repoPath + gitRepo + ';git checkout ' + gitBranch + '; git pull"',
                    returnStdout: true)
            println(" git pull result : ")
            println(pullLatest)

            execParallel(arrIndex, label, osVersion)
        } else {
            name = getSlaveObj(parameters["nodeName"]).name
            description = getSlaveObj(parameters["nodeName"]).getNodeDescription()
            label = getSlaveObj(parameters["nodeName"]).getLabelString()
            setSlaveState(name, true)
            doSlaveProvisioning(name,label,description,osVersion);
        }
    }catch(any){
        throw any
    }

}

def setThreadCount( Integer count)
{
    semaphoreThreadCount = count
    init=-(semaphoreThreadCount)
    threadCount = semaphoreThreadCount
}

def execParallel(arrIndex, label, osVersion){
    try {
        init = init + threadCount
        endPoint = endPoint + threadCount
        parallel create_array(arrIndex, label, osVersion)
    }catch(any){
        throw any
    }
}

def isResourceLocked(resource)
{
    if(type=="AT")
    {
        if(!(resource.isLocked()) && !(resource.isReserved()))
        {
            return 1;
        }
        else
        {
            return 0;
        }
    }
    else
    {
        if(!getSlaveObj(resource).getComputer().isOffline() && getSlaveObj(resource).getComputer().countBusy() == 0)
        {
            return 1;
        }
        else
        {
            return 0;
        }
    }
}
def create_array(arrIndex, label, osVersion)
{
    Map validationInParallel = [:]
    if(init+threadCount <= matchedResources.size() && flag == true){// provision untill there is less than 15 VM's in matchedResources
        for (int i = init;i<matchedResources.size() && i <init+threadCount; i++) { //
            def available = isResourceLocked(matchedResources[i])
            if(available){
                countArr[arrIndex] = 0
                print "Entering parellel execution "+ matchedResources[i]
                if(type == "AT") {
                    validationInParallel[matchedResources[i]] = parallel_semaphore(matchedResources[i], arrIndex, label, osVersion)
                }
                else {
                    label = getSlaveObj(matchedResources[i]).getLabelString()
                    name = getSlaveObj(matchedResources[i]).name
                    description = getSlaveObj(matchedResources[i]).getNodeDescription()
                    setSlaveState(name, true)
                    validationInParallel[matchedResources[i]] = doSlaveProvisioning(arrIndex, label, osVersion, name, description)
                }
            }
            else { //locked! push to block state
                blocked_list[position] = matchedResources[i]
                position = position + 1
                matchedResources.remove(matchedResources[i])
                i = i - 1
                print "Blocked list "+ blocked_list
                print "Matched resources list"+ matchedResources
            }
        }
    }
    if(init+threadCount <= matchedResources.size() && flag == false || init+threadCount > matchedResources.size() && flag == true || init+threadCount > matchedResources.size() && flag == false || matchedResources.size()==0 )
    {// less than 15 vms
        if (flag == true) { //first provison Vm's from matchedResources
            endPoint = (init + threadCount) - (matchedResources.size())
            for (int i = init; i < matchedResources.size(); i++) {
                waiting = true
                def available = isResourceLocked(matchedResources[i])
                if(available){
                    countArr[arrIndex] = 0
                    print "Entering parellel execution " + matchedResources[i]
                    if(type == "AT") {
                        validationInParallel[matchedResources[i]] = parallel_semaphore(matchedResources[i], arrIndex, label, osVersion)
                    }
                    else {
                        label = getSlaveObj(matchedResources[i]).getLabelString()
                        name = getSlaveObj(matchedResources[i]).name
                        description = getSlaveObj(matchedResources[i]).getNodeDescription()
                        setSlaveState(name, true)
                        validationInParallel[matchedResources[i]] = doSlaveProvisioning(arrIndex, label, osVersion, name, description)
                    }

                } else { //locked! push to block state
                    blocked_list[position] = matchedResources[i]
                    position = position + 1
                    matchedResources.remove(matchedResources[i])
                    i = i - 1
                    print "Blocked list " + blocked_list
                    print "Matched resources list " + matchedResources
                }
            }
            start_point = 0
            flag = false
        }
        blocked_pointer = 0
        if (blocked_list.size > threadCount) // if any place in parellel process free then push blocked vms(free) to there
        {
            for (int i = start_point;i < blocked_list.size() && i<endPoint ; i++) {
                if(blocked_pointer >= blocked_list.size() && waiting == true)
                {
                    waiting = false
                    break
                }
                else if (blocked_pointer >= blocked_list.size() && waiting == false)
                {
                    blocked_pointer=0
                    i = start_point
                    sleep(180)
                }
                def available = isResourceLocked(blocked_list[i])
                if(available){
                    waiting = true
                    print "Blocked pointer is " + blocked_pointer
                    countArr[arrIndex] = 0
                    print "Entering parellel execution "+ blocked_list[i]
                    if(type == "AT") {
                        validationInParallel[blocked_list[i]] = parallel_semaphore(blocked_list[i],arrIndex,label,osVersion)
                    }
                    else {
                        label = getSlaveObj(blocked_list[i]).getLabelString()
                        name = getSlaveObj(blocked_list[i]).name
                        description = getSlaveObj(blocked_list[i]).getNodeDescription()
                        setSlaveState(name, true)
                        validationInParallel[blocked_list[i]] = doSlaveProvisioning(arrIndex, label, osVersion, name, description)
                    }
                    blocked_list.remove(blocked_list[i])
                    position = position -1
                    print "size"+ blocked_list.size
                    print "blocked list after removing" + blocked_list
                    i = i - 1
                    endPoint = endPoint - 1
                }
                else { //locked! remove and append to last
                    blocked_pointer = blocked_pointer + 1
                    temp_var = blocked_list[i]
                    blocked_list.remove(blocked_list[i])
                    position = position - 1
                    blocked_list[position] = temp_var
                    position = position + 1
                    i = i - 1
                    print "Blocked list is "+ blocked_list
                }
                start_point = i + 1
            }
        }
        else { //if less than 15 Vms are in blocked state then simple start parellel process
            for (int i = start_point;i < blocked_list.size() && i<endPoint ; i++) {
                print "Entering parellel execution "+ blocked_list[i]
                countArr[arrIndex] = 0
                if(type == "AT") {
                    validationInParallel[blocked_list[i]] = parallel_semaphore(blocked_list[i],arrIndex,label,osVersion)
                }
                else {
                    label = getSlaveObj(blocked_list[i]).getLabelString()
                    name = getSlaveObj(blocked_list[i]).name
                    setSlaveState(name, true)
                    description = getSlaveObj(blocked_list[i]).getNodeDescription()
                    validationInParallel[blocked_list[i]] = doSlaveProvisioning(arrIndex, label, osVersion, name, description)
                }
                start_point = i + 1
            }
        }
    }
    return validationInParallel
}

def parallel_semaphore(resource,arrIndex, label, osVersion) {
    return {
        while (true) {
            if (semaphore.tryAcquire()) {
                try {
                    lock(resource.getName()) {
                        timeout(time: 3600, unit: 'SECONDS') {
                            println("resource.getDescription()" + resource.getDescription())
                            description = resource.getDescription().trim()
                            ip = getIp(description)
                            ip_log = ip
                            println(ip_log)
                            osVersion = "18.04"
                            if (gitBranch == "master") {
                                k8sVersion = "1.18.13"
                            }
                            else{
                                k8sVersion = "1.19.8"
                            }
                            println('sshpass -p' + password + ' ssh -o StrictHostKeyChecking=no ' + username + '@' + cloudMapping[cloudName] + ' "cd ' + repoPath + gitRepo + ';./rebuildVM.sh ' + ip + ' ' + flavor + ' ' + cloudName + ' ' + csl + ' ' + osVersion + ' ' + flavor + ' ' + k8sVersion + '"')
                            try {
                                def cleanupScriptStatus = sh(
                                        script: 'sshpass -p' + password + ' ssh -o StrictHostKeyChecking=no ' + username + '@' + cloudMapping[cloudName] + ' "cd ' + repoPath + gitRepo + ';./rebuildVM.sh ' + ip + ' ' + flavor + ' ' + cloudName + ' ' + csl + ' ' + osVersion + ' ' + flavor + ' ' + k8sVersion + '"',
                                        returnStdout: true
                                )
                                println(cleanupScriptStatus);
                                ip_log = ip
                                def logStatus = showATProvisionLog(ip_log)
                                if (cleanupScriptStatus.contains("error in rebuild") || !(cleanupScriptStatus.contains("vm provisioned properly"))){
                                    println(logStatus)
                                    errorVms.add(resource.getName())
                                    error "Clean up of automation or Jenkins didn't complete successfully"

                                } else if (label.toLowerCase().contains("_fault")) {
                                    println("Resource label before provisioning : " + label)
                                    def label_change = label.substring(0, label.toLowerCase().indexOf("_fault"))
                                    resource.setLabels(label_change)
                                    println("Resource label after provisioning : " + label_change)
                                    retry(2) {
                                        def newLabel = resource.getLabels()
                                        println("new label is :" + newLabel)
                                        if (label_change == newLabel) {
                                            println("Label changed properly")
                                            sleep(60)
                                        } else {
                                            sleep(60)
                                            error "VM label didn't changed"
                                        }
                                    }
                                    echo "_Fault or _FaultProvisioning removed from label as the provision is success"

                                } else if (label.contains("_DO_NOT_PROVISION")) {
                                    println("Resource label before provisioning : " + label)
                                    def label_change = label.replace("_DO_NOT_PROVISION", "")
                                    println("Resource label after provisioning : " + label_change)
                                    resource.setLabels(label_change)
                                    retry(2) {
                                        def newLabel = resource.getLabels()
                                        println("new label is :" + newLabel)
                                        if (label_change == newLabel) {
                                            println("Label changed properly")
                                            sleep(60)
                                        } else {
                                            sleep(60)
                                            error "VM label didn't changed"
                                        }
                                    }
                                    echo "_DO_NOT_PROVISION removed from label as the provision is success"
                                } else {
                                    echo "VM provisioned properly by pipeline"
                                }
                            } catch (any) {
                                print "final catched while provisioning"
                                //attach to faulty label
                                //throw any
                                currentBuild.result = "FAILURE"
                                currentBuild.description = "there are AT provisioning failures"
                                //error "Clean up of automation or Jenkins didn't complete successfully, changing the label for " + resource.getName() + " as " + any
                            }
                        }
                    }
                    break
                }
                catch (any) {
                    throw any
                } finally {
                    print "release"+ resource
                    semaphore.release()
                    countArr[arrIndex] = countArr[arrIndex] + 1
                    if (countArr[arrIndex] == 1) {
                        arrIndex = arrIndex + 1
                        print "Trying to Create new "+ semaphoreThreadCount +" threads initially"
                        execParallel(arrIndex, label, osVersion)
                    }
                }
            }
        }
    }
}


def getIp(description){
    if(description.contains("sshpass")) {
        description = description.replaceAll("\"", "")
        publicIp = description.tokenize(";")[0].tokenize("-")[3]
        println(publicIp)
        return publicIp
    }else{
        return description
    }
}
def slaveCleanup(nodeName) {
    name=getSlaveObj(nodeName).name
    command=getSlaveObj(nodeName).getNodeDescription()
    label=getSlaveObj(nodeName).getLabelString()
    setSlaveState(name,true)
    doSlaveProvisioning(name,label,command);
}


def doSlaveProvisioning(name,label,description,osVersion) {
    try {
        descriptionArr=description.split(",")
        publicIp = descriptionArr[0]
        cloudName = descriptionArr[1]
        osVersion="18.04"
        if (gitBranch == "master") {
            k8sVersion = "1.18.13"
        }
        else{
            k8sVersion = "1.19.8"
        }
        println("os version - " + osVersion)
        println("public ip - " + publicIp)
        println("cloud - " + cloudName)
        pullLatest = sh(
                script: 'sshpass -p' + password + ' ssh -o StrictHostKeyChecking=no ' + username + '@' + cloudMapping[cloudName] + ' "cd ' + repoPath + gitRepo + ';git checkout ' + gitBranch + '; git pull"',
                returnStdout: true)
        println(" git pull result : ")
        println(pullLatest)
        def cleanupScriptStatus = sh (
                script: 'sshpass -p' + password +' ssh -o StrictHostKeyChecking=no ' + username + '@' + cloudMapping[cloudName.trim()] + ' "cd ' + repoPath + gitRepo +';./rebuildVM.sh ' +  publicIp + ' ' + flavor + ' ' +  cloudName + ' ' + csl  + ' ' + osVersion + ' ' + flavor + ' ' + k8sVersion + '"',
                returnStdout: true
        )
        println(cleanupScriptStatus);
        if (cleanupScriptStatus.contains("error in rebuild")) {
            showProvisionLog(publicIp)
            error "Clean up of slave didn't complete successfully"
        }
        else if(cleanupScriptStatus.contains("vm provisioned properly")){
            print "Slave VM provisioned properly by pipeline"
            setSlaveState(name,false);
            if (label.contains("_FaultProvisioning")) {
                print "Remove _FaultProvisioning from node "+name+" with Label "+label
                setSlaveLabel(name,label.replace("_FaultProvisioning",""))
            }
        }else{
            showProvisionLog(publicIp)
            error "Clean up of slave didn't complete successfully"
        }
    } catch(any) {
        if(!label.contains("_FaultProvisioning")) {
            setSlaveState(name,false);
            echo "[ERROR]-Cleanup failed!!-Label "+label+" of node "+name+" is appended with _FaultProvisioning"
        }
    }
}

def getSlaveObj(nodeName) {
    Jenkins jenkins = Jenkins.instance
    def jenkinsNodes = jenkins.nodes
    for (node in jenkinsNodes) {
        if (node.getLabelString() == nodeName) {
            return node
        }
    }
}


def setSlaveState(name,state) {
    Jenkins jenkins = Jenkins.instance
    def jenkinsNodes = jenkins.nodes
    for (node in jenkinsNodes) {
        if (node.name == name) {
            if (state == true) {
                while(true){
                    if(node.getComputer().countBusy()==0){
                        node.getComputer().setTemporarilyOffline(true,null);
                        node.getComputer().disconnect()
                        print node.getLabelString()+" node is made temperorily offline"
                        break
                    }
                    else{
                        print node.getLabelString()+"node is busy. will retry after 5 minutes"
                        sleep(300)
                    }
                }
            }
            else {
                node.getComputer().setTemporarilyOffline(false);
                node.getComputer().launch()
//                node.getComputer().connect(true)
                print node.getLabelString()+" node is made online again"
            }
        }
    }
}
def setSlaveLabel(name,label) {
    Jenkins jenkins = Jenkins.instance
    def jenkinsNodes = jenkins.nodes
    for (node in jenkinsNodes) {
        if (node.name == name) {
            node.setLabelString(label);
            print "Node "+name+" label is renamed to "+node.getLabelString();
        }
    }
}

def showProvisionLog(ipLog){
    try {
        println("ERROR !!!")
        def checklogStatus = sh (
                script: 'sshpass -p' + password +' ssh -o StrictHostKeyChecking=no ' + username + '@' + cloudMapping[cloudName.trim()] + ' "ls -la /home/fnmst001/ansibleProvisionLogs/ansibleRunLog-' + ipLog + '*' + '"',
                returnStdout: true
        )
        println("Read logs:")
        println(checklogStatus);
        def logStatus = sh (
                script: 'sshpass -p' + password +' ssh -o StrictHostKeyChecking=no ' + username + '@' + cloudMapping[cloudName.trim()] + ' "cat /home/fnmst001/ansibleProvisionLogs/ansibleRunLog-' + ipLog + '*' + '"',
                returnStdout: true
        )
        println("Content logs:")
        println(logStatus);
    } catch (any){
        println("ERROR when show log as " + any)
    }
}

def showATProvisionLog(ipLog){
    println(ipLog)
    try {
        def logStatus = sh (
                script: 'sshpass -p' + password +' ssh -o StrictHostKeyChecking=no ' + username + '@' + cloudMapping[cloudName.trim()] + ' "cat /home/fnmst001/ansibleProvisionLogs/ansibleRunLog-' + ipLog + '*' + '"',
                returnStdout: true
        )
        return logStatus
    } catch (any){
        println("ERROR when show log as " + any)
        return null
    }
}
