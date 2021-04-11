# Native2Java
Automatically transfer native API to Java Code


## Setup
The following is required to set up Native2Java:
* MAC system
* Intellj

##### Step 1: Load repo
* git clone git@github.com:sunxiaobiu/Native2Java.git
* cd Native2Java

##### Step 2: build package：
mvn clean install

##### Step 3: example of running Native2Java(4 parameters):
* Parameters are needed here: [your_apk_path.apk],[path of android.jar],[path of os.result files],[path of instrumented file]
* Example: your_path/xxx.apk, your_path/android-platforms/, your_path/, your_path/
       
   
## Output
* Refer to [path of instrumented file] folder to obtain the instrumented APK.

##Tips
* This tool use aapt to load manifest of APK, so you need to set appt as an environmental variable as follow:

AAPT_HOME=/your_local_path/Library/Android/sdk/build-tools/29.0.2
export AAPT_HOME
export PATH=$PATH:$AAPT_HOME