# CRaCing Apache Spark

This document show how Apache Spark can use CRaC to run jobs in a pre-warmed environment, using `JavaWordCount` example.
We achieve that by automatically writing down a checkpoint after the Driver and Executor finish, and restoring from
this checkpoint for a subsequent task.

1. Download and unpack Apache Spark release:
```
mkdir /tmp/spark && cd /tmp/spark
wget https://dlcdn.apache.org/spark/spark-3.5.4/spark-3.5.4-bin-hadoop3.tgz
tar xzf spark-3.5.4-bin-hadoop3.tgz
```

2. Clone forked repo, build modified modules and overwrite them in the distribution from above
```
git clone --branch crac --single-branch --depth 1 https://github.com/rvansa/spark
cd spark && ./build/mvn package -DskipTests=true -Dmaven.test.skip=true -Dcyclonedx.skip=true \
    -pl :spark-core_2.12,:spark-launcher_2.12,:spark-network-common_2.12 && cd ..
cp spark/core/target/spark-core_2.12-3.5.4.jar spark-3.5.4-bin-hadoop3/jars/
cp spark/common/network-common/target/spark-network-common_2.12-3.5.4.jar spark-3.5.4-bin-hadoop3/jars/
cp spark/launcher/target/spark-launcher_2.12-3.5.4.jar spark-3.5.4-bin-hadoop3/jars/
```

3. Add org.crac dependency
```
wget -O spark-3.5.4-bin-hadoop3/jars/crac-1.5.0.jar https://repo1.maven.org/maven2/org/crac/crac/1.5.0/crac-1.5.0.jar
```

4. Extract native ZSTD decompression library from one of the JARS:
```
unzip -p spark-3.5.4-bin-hadoop3/jars/zstd-jni-1.5.5-4.jar linux/amd64/libzstd-jni-1.5.5-4.so > libzstd-jni-1.5.5-4.so
```

5. Go to the distribution directory and create configuration file with checkpoint locations
```
cd spark-3.5.4-bin-hadoop3
vi conf/spark-defaults.conf
```
```
spark.executor.extraJavaOptions    -Djava.security.manager=allow -DZstdNativePath=/tmp/spark/libzstd-jni-1.5.5-4.so
spark.executor.checkpointLocation  /tmp/spark-executor-cr
spark.driver.extraJavaOptions      -Djava.security.manager=allow --add-opens=java.base/sun.nio.ch=ALL-UNNAMED -Dlz4java.jnilib.temp.keep=true
spark.driver.checkpointLocation    /tmp/spark-driver-cr
```

We use `-Djava.security.manager=allow` on multiple places; Apache Spark 3.5.4 is not compatible with recent JDKs,
this option is not really relevant to CRaC.

6. Start master and one worker locally
```
export JAVA_HOME=/path/to/Azul/JDK
SPARK_MASTER_OPTS="-Djava.security.manager=allow" sbin/start-master.sh
SPARK_WORKER_OPTS="-Djava.security.manager=allow" sbin/start-worker.sh spark://$(hostname):7077
```

7. Run the example twice. After the first run you should notice `/tmp/spark-executor-cr` and `/tmp/spark-driver-cr`
created; in the second run these should be used.
```
SPARK_SUBMIT_OPTS="-Djava.security.manager=allow" bin/spark-submit --deploy-mode cluster --master spark://$(hostname):7077 \
    --class org.apache.spark.examples.JavaWordCount examples/jars/spark-examples_2.12-3.5.4.jar $(pwd)/README.md
SPARK_SUBMIT_OPTS="-Djava.security.manager=allow" bin/spark-submit --deploy-mode cluster --master spark://$(hostname):7077 \
    --class org.apache.spark.examples.JavaWordCount examples/jars/spark-examples_2.12-3.5.4.jar $(pwd)/LICENSE
```
You can open `http://127.0.0.1:8080` to see Spark Master console and verify commands output and logs.

Note that we have used `--deploy-mode cluster` - it is possible to use `client` mode as well, but you should wipe out
the driver checkpoint location (`/tmp/spark-driver-cr`) before switching the mode.
