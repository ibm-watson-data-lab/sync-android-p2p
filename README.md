# sync-android-p2p

[![Build Status](https://travis-ci.org/snowch/sync-android-p2p.png?branch=master)](https://travis-ci.org/snowch/sync-android-p2p)

This project implements a http listener on top of sync-android to allow sync-android to become a replication target.

This project is a prototype.  It can perform a basic replication based on the steps below, however, a lot more functionality needs to be implemented to support replication proper.  In theory this code should work on Android, but this has not been tried yet.

Expect lots of bugs, this code needs much more testing.

### Testing this project

- check out this project
```
git clone https://github.com/snowch/sync-android-p2p
cd sync-android
./gradlew test
```

### Using Eclipse

If you are using eclipse, execute below command. For Android studio, skip to *Using Android Studio* section

```
./gradlew eclipse
```

- In Eclipse, create a Run Configuration to run P2PTest.java.  Ensure the VM Arguments are set to this for the test:

```
-ea -Dsqlite4java.library.path=native
```

### Using Android Studio

To run JUnit tests in Android Studio you will need to set VM arguments in the following way:

- In menu, select Run -> Edit configurations
- In Edit configurations screen select Defaults -> JUnit
- At right hand panel add ```-ea -Dsqlite4java.library.path=native``` as VM Options

### Deploying this project's jars to the github repo

```
./gradlew clean build uploadArchives
git add repository/
git commit ...
git push 
```

### Using this project's jars from another project 

Example build.gradle:


```
apply plugin: 'android'

android {
  compileSdkVersion 19
  buildToolsVersion '19.0.0'

  repositories {
    mavenLocal()
    mavenCentral()
    maven { url "http://maven.restlet.org" }
    maven { url "http://cloudant.github.io/cloudant-sync-eap/repository/" }
    maven { url 'https://github.com/snowch/sync-android-p2p/raw/master/repository/' }
  }

  ...
    
  dependencies {
    compile 'net.christophersnow:sync-android-p2p:0.0.5-SNAPSHOT'
  }
}
```

See example project: [./sync-android-p2p-example](./sync-android-p2p-example)
