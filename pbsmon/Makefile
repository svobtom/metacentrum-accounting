JAVA_HOME=/usr/lib/jvm/java-8-oracle
CFLAGS_TOR=-g -I$(TOR_HOME)/include -L/usr/lib -I$(JAVA_HOME)/include/ -I$(JAVA_HOME)/include/linux 
CFLAGS_PRO=-g -I./include_pro -L/usr/lib -I$(JAVA_HOME)/include/ -I$(JAVA_HOME)/include/linux 
#LDFLAGS=-lcmds -lpbs 

#RUNJAVAFLAGS=-verbose:jni

all: target/pbsprocaller

pbspro: target/libpbsmon2pro.so

torque: target/libpbsmon2torque.so

#all: target/libpbsmon2pro.so target/libpbsmon2torque.so

#all: target/libpbsmon2torque.so

target/pbsprocaller: src/main/c/pbsprocaller.c
	$(CC) -g -L/usr/lib src/main/c/pbsprocaller.c -o target/pbsprocaller -lpbs

run: target/pbsprocaller
	java $(RUNJAVAFLAGS) -classpath $(PWD)/target/classes/:$(PWD)/target/pbsmon2/WEB-INF/lib/* cz.cesnet.meta.pbs.PbsConnectorFile

run2: target/libpbsmon2pro.so
	LD_LIBRARY_PATH=$(PWD)/target java $(RUNJAVAFLAGS) -classpath $(PWD)/target/classes/:$(PWD)/target/pbsmon2/WEB-INF/lib/* cz.cesnet.meta.pbs.PbsConnectorJNI
#	LD_LIBRARY_PATH=$(PWD)/target java $(RUNJAVAFLAGS) -classpath $(PWD)/target/classes/:$(PWD)/target/pbsmon2/WEB-INF/lib/* -Dcatalina.base=/tmp cz.cesnet.meta.pbs.PbsConnectorJNI

target/libpbsmon2torque.so: target/cz_cesnet_meta_pbs_PbsConnectorJNI.h src/main/c/cz_cesnet_meta_pbs_PbsConnectorJNITorque.c
	$(CC) $(CFLAGS_TOR) -L$(PWD)/target -I./target -shared -fpic src/main/c/cz_cesnet_meta_pbs_PbsConnectorJNITorque.c -o target/libpbsmon2torque.so -ltorque

target/libpbsmon2pro.so:    target/cz_cesnet_meta_pbs_PbsConnectorJNI.h src/main/c/cz_cesnet_meta_pbs_PbsConnectorJNIPro.c
	$(CC) $(CFLAGS_PRO) -L$(PWD)/target -I./target -shared -fpic src/main/c/cz_cesnet_meta_pbs_PbsConnectorJNIPro.c -o target/libpbsmon2pro.so -lpbs

target/cz_cesnet_meta_pbs_PbsConnectorJNI.h: target/classes/cz/cesnet/meta/pbs/PbsConnectorJNI.class
	javah -force -jni -d target -classpath target/classes cz.cesnet.meta.pbs.PbsConnectorJNI

target/classes/cz/cesnet/meta/pbs/PbsConnectorJNI.class: src/main/java/cz/cesnet/meta/pbs/PbsConnectorJNI.java
	mvn package
clean:
	rm -f target/lib* target/pbsprocaller
