# Native2Java
Automatically transfer native API to Java Code


## Setup
The following is required to set up APIUnitTest:
* MAC system
* Intellj

##### Step 1: Load repo
* git clone git@github.com:sunxiaobiu/Native2Java.git
* cd Native2Java

##### Step 2: build package：
mvn clean install

##### Step 3: example of running Native2Java(4 parameters):
* Parameters are needed here: [your_apk_path.apk],[path of android.jar],[path of os.result files],[path of instrumented file]
* Example: your_path/905a4f82bc194334a046afa9bf29eed7.apk, your_path/android-platforms/android-17/android.jar, your_path/, your_path/
       
   
## Output
* Refer to [path of instrumented file] folder to obtain the instrumented APK.
