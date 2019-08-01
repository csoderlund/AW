JAVA_PATH=/usr/java/latest
#JAVA_PATH=/Library/Java/JavaVirtualMachines/jdk1.7.0_67.jdk/Contents/Home
JARNAME = aw.jar

JAVA_FILES=./src/build/*.java \
			./src/build/compute/*.java \
			./src/build/panels/*.java \
           ./src/build/file/*.java \
           ./src/database/*.java \
           ./src/util/*.java \
           ./src/viewer/*.java \
           ./src/viewer/panels/*.java \
           ./src/viewer/controls/*.java \
           ./src/viewer/table/*.java \
          ./src/tools/*.java 


SOURCES = ./src/build:./src/util:./src/database:./src/viewer:./src/tools -d ./classes $(JAVA_FILES)

CLASSES_EXT = -C ./classes_ext netscape -C ./classes_ext sun -C ./classes_ext com 
CLASSES =  -C ./classes build -C ./classes util -C ./classes database -C ./classes viewer  -C ./classes tools -C ./src html  -C ./src images

all: build 

build :
	$(JAVA_PATH)/bin/javac  -sourcepath $(SOURCES)
	$(JAVA_PATH)/bin/jar -cfm jars/$(JARNAME) manifest.add $(CLASSES) $(CLASSES_EXT)

	
clean:
	rm -rf classes/*

